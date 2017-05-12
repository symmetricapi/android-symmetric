package com.symmetric.api;

import android.util.Log;

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.ProtocolException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Mac;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Performs requests for HttpURLConnections and processes the response.
 * Ensures that there is a valid session otherwise waits for the APISession to renew if loginRequired.
 */
public final class APIURLConnection
{
	static final String CONTENT_TYPE_JSON = "application/json";
	static final String CONTENT_TYPE_FORM = "application/x-www-form-urlencoded";

	static final String HEADER_ACCEPT = "Accept";
	static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";
	static final String HEADER_CONTENT_TYPE = "Content-Type";
	static final String HEADER_CSRF_TOKEN = "X-CSRFToken";
	static final String HEADER_COOKIE = "Cookie";
	static final String HEADER_REFERER = "Referer";
	static final String XHEADER_NATIVE_APP = "X-Native-App";
	static final String XHEADER_HMAC = "X-Hmac";
	static final String XHEADER_NONCE = "X-Hmac-Nonce";

	static final String METHOD_READ = "GET";
	static final String METHOD_CREATE = "POST";
	static final String METHOD_UPDATE = "PUT";
	static final String METHOD_DELETE = "DELETE";

	static final String COOKIE_SESSION_ID = "sessionid";
	static final String COOKIE_CSRF_TOKEN = "csrftoken";

	private static String acceptLanguage;
	private static String nativeApp;

	HttpURLConnection connection;
	private int action;
	private String path;
	private APIRequestParams params;
	private byte[] data;
	private boolean https;
	private boolean sign;
	private boolean loginRequired;
	private HashMap<String, String> requestProperties = new HashMap<String, String>();
	private boolean aborted;

	public APIURLConnection(int action, String path, APIRequestParams params, byte[] data, boolean https, boolean loginRequired, boolean sign)
	{
		this.action = action;
		this.path = path;
		this.params = params;
		this.data = data;
		this.https = https;
		this.sign = sign;
		this.loginRequired = loginRequired;
	}

	public String execute() throws IOException
	{
		String response = null;

		// Wait for a valid session
		if(this.loginRequired && APISession.getSharedSession().waitForLogin(this))
		{
			try {
				synchronized(this) { this.wait(); }
			} catch(InterruptedException e) { return null; }
		}

		try
		{
			newConnection();
			if(this.data != null && this.data.length > 0)
			{
				OutputStream out;
				this.connection.setDoOutput(true);
				this.connection.setFixedLengthStreamingMode(this.data.length);
				out = this.connection.getOutputStream();
				out.write(this.data);
				out.close();
			}
			else
			{
				this.connection.connect();
			}

			// Try with a new connection if the request failed due to the session
			if(this.loginRequired && !APISession.getSharedSession().processResponse(this))
				return execute();

			if(this.connection.getResponseCode() >= 400)
				response = API.convertStreamToString(this.connection.getErrorStream());
			else if(this.action == API.ACTION_LIST || this.action == API.ACTION_READ)
				response = API.convertStreamToString(this.connection.getInputStream());

			if(this.params != null)
				this.params.processResponse(this);
		}
		catch(IOException e) { throw e; }
		finally { this.connection.disconnect(); }

		return response;
	}

	public void abort()
	{
		this.connection.disconnect();
		this.aborted = true;
	}

	public boolean isAborted()
	{
		return this.aborted;
	}

	public int getResponseCode() throws IOException
	{
		return this.connection.getResponseCode();
	}

	public String getHeaderField(String key)
	{
		return this.connection.getHeaderField(key);
	}

	public void setRequestProperty(String field, String newValue)
	{
		this.requestProperties.put(field, newValue);
	}

	public String getRequestProperty(String field)
	{
		return this.requestProperties.get(field);
	}

	void newConnection() throws IOException
	{
		boolean httpsOnly;
		String file, method, queryString = null;
		URL url;
		StringBuilder cookies;
		APISession session;

		// Cleanup
		if(this.connection != null)
			this.connection.disconnect();

		// Build the URL and create the connection with the correct method
		httpsOnly = Boolean.parseBoolean(API.getConfiguration(API.CONFIG_HTTPS_ONLY));
		if(this.params != null)
			queryString = API.encodeUrlArgs(this.params.getArgs());
		if(queryString != null && queryString.length() > 0)
			file = String.format("%s?%s", this.path, queryString);
		else
			file = this.path;
		try
		{
			url = new URL(String.format("%s://%s%s", ((httpsOnly || this.https) ? "https" : "http"), API.getConfiguration(API.CONFIG_HOST), file));
		}
		catch(MalformedURLException e) { return; }
		this.connection = (HttpURLConnection)url.openConnection();
		this.aborted = false;
		switch(this.action)
		{
			case API.ACTION_CREATE:
				method = METHOD_CREATE;
				break;
			case API.ACTION_UPDATE:
				method = METHOD_UPDATE;
				break;
			case API.ACTION_DELETE:
				method = METHOD_DELETE;
				break;
			case API.ACTION_READ:
			default:
				method = METHOD_READ;
				break;
		}
		try
		{
			this.connection.setRequestMethod(method);
		}
		catch(ProtocolException e) { }

		// Add the default native app flag
		if(nativeApp == null)
			nativeApp = String.format("Android %s; %s; %s", android.os.Build.VERSION.RELEASE, getDeviceName(), API.appPackage);
		this.connection.setRequestProperty(XHEADER_NATIVE_APP, nativeApp);

		// Add the Accept content type header
		this.connection.setRequestProperty(HEADER_ACCEPT, CONTENT_TYPE_JSON);

		// Add the user's locale to each request
		if(acceptLanguage == null)
			acceptLanguage = API.appContext.getResources().getConfiguration().locale.getLanguage();
		if(acceptLanguage.length() > 0)
			this.connection.setRequestProperty(HEADER_ACCEPT_LANGUAGE, acceptLanguage);

		// Add the Referer header as required by csrf
		this.connection.setRequestProperty(HEADER_REFERER, String.format("%s://%s", ((httpsOnly || this.https) ? "https" : "http"), API.getConfiguration(API.CONFIG_HOST)));

		// Add the session and csrf cookies and headers
		session = APISession.getSharedSession();
		cookies = new StringBuilder();
		if(session.sessionid != null && session.sessionid.length() > 0)
			cookies.append(COOKIE_SESSION_ID + "=" + session.sessionid);
		if(session.csrfToken != null && session.csrfToken.length() > 0)
		{
			if(cookies.length() > 0)
				cookies.append("; ");
			cookies.append(COOKIE_CSRF_TOKEN + "=" + session.csrfToken);
			this.connection.setRequestProperty(HEADER_CSRF_TOKEN, session.csrfToken);
		}
		if(cookies.length() > 0)
			this.connection.setRequestProperty(HEADER_COOKIE, cookies.toString());

		if(this.data != null && this.data.length > 0)
		{
			// Set the content type. The Content-Length header is set automatically with setFixedLengthStreamingMode, see the source for sun.net.www.protocol.http.HttpURLConnection
			this.connection.setRequestProperty(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
			// Sign the request
			if(this.sign)
				signRequest();
		}

		// Add an extra headers set outside of this class
		for(Entry<String, String> entry : this.requestProperties.entrySet())
		{
			this.connection.setRequestProperty(entry.getKey(), entry.getValue());
		}
	}

	private static String capitalizeString(String s)
	{
		if(s == null || s.length() == 0)
			return "";
		char first = s.charAt(0);
		if(Character.isUpperCase(first))
			return s;
		else
			return Character.toUpperCase(first) + s.substring(1);
	}

	// http://stackoverflow.com/questions/1995439/get-android-phone-model-programmatically
	private static String getDeviceName()
	{
		String manufacturer = android.os.Build.MANUFACTURER;
		String model = android.os.Build.MODEL;
		if(model.startsWith(manufacturer))
			return capitalizeString(model);
		else
			return capitalizeString(manufacturer) + " " + model;
	}

	private void signRequest()
	{
		SecretKey key;
		Mac mac;
		String hmac;
		String salt;
		int i;
		char c;

		try
		{
			key = new SecretKeySpec(API.getConfiguration(API.CONFIG_HMAC_KEY).getBytes(), "HmacSHA256");
			mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			for(i = (this.data.length - 1); i > 0; --i)
			{
				c = (char)this.data[i];
				if((c != '\n') && (c != '\r'))
					break;
			}
			mac.update(this.data, 0, i + 1);
			// Add a salt if set
			salt = API.getConfiguration(API.CONFIG_HMAC_SALT);
			if(salt != null)
				mac.update(salt.getBytes());
			hmac = API.byteArrayToString(mac.doFinal());
			this.connection.setRequestProperty(XHEADER_HMAC, hmac);
		} catch(Exception e) { Log.e(API.TAG, "HMAC failed with error: " + e.getMessage()); }
	}

	private void signRequestUsingNonce()
	{
		SecretKey key;
		Mac mac;
		String hmac, timestamp, nonce;
		String salt;
		int i;
		char c;

		try
		{
			key = new SecretKeySpec(API.getConfiguration(API.CONFIG_HMAC_KEY).getBytes(), "HmacSHA256");
			mac = Mac.getInstance("HmacSHA256");
			mac.init(key);
			for(i = (this.data.length - 1); i > 0; --i)
			{
				c = (char)this.data[i];
				if((c != '\n') && (c != '\r'))
					break;
			}
			mac.update(this.data, 0, i + 1);
			// Add a salt if set
			salt = API.getConfiguration(API.CONFIG_HMAC_SALT);
			if(salt != null)
				mac.update(salt.getBytes());
			// Add a nonce
			nonce = UUID.randomUUID().toString();
			mac.update(nonce.getBytes());

			hmac = API.byteArrayToString(mac.doFinal());
			this.connection.setRequestProperty(XHEADER_HMAC, hmac);
			this.connection.setRequestProperty(XHEADER_NONCE, nonce);
		} catch(Exception e) { Log.e(API.TAG, "HMAC failed with error: " + e.getMessage()); }
	}
}
