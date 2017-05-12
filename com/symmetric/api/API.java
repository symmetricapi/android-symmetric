// Requires LocalBroadcastManager.java, found in the following location:
// <android-sdk>/extras/android/support/v4/src/java/android/support/v4/content/LocalBroadcastManager.java
package com.symmetric.api;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.IOException;
import java.io.File;
import java.net.URLEncoder;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.HashMap;
import java.util.Random;
import java.util.Scanner;
import java.util.TimeZone;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.json.JSONObject;

public final class API
{
	public static final String CONFIG_HOST = "HOST";
	public static final String CONFIG_HTTPS_LOGIN = "HTTPS_LOGIN";
	public static final String CONFIG_HTTPS_ONLY = "HTTPS_ONLY";
	public static final String CONFIG_CURRENT_USER_URL = "CURRENT_USER_URL";
	public static final String CONFIG_LOGIN_URL = "LOGIN_URL";
	public static final String CONFIG_LOGOUT_URL = "LOGOUT_URL";
	public static final String CONFIG_HMAC_KEY = "HMAC_KEY";
	public static final String CONFIG_HMAC_SALT = "HMAC_SALT";
	public static final String CONFIG_MOBILE_KEY = "MOBILE_KEY";
	public static final String CONFIG_FILTER_CONTACTS_URL = "FILTER_CONTACTS_URL";
	public static final String CONFIG_CREATE_USER_URL = "CREATE_USER_URL";
	public static final String CONFIG_SET_PASSWORD_URL = "SET_PASSWORD_URL";
	public static final String CONFIG_RESET_PASSWORD_URL = "RESET_PASSWORD_URL";

	public static final String EXTRA_ERROR = "ERROR";
	public static final String ERROR_NOINTERNET = "No internet connection available.";
	public static final String ERROR_BADCONNECTION = "Could not establish connection.";
	public static final String ERROR_BADRESPONSE = "There was a problem with response from the server.";
	public static final String ERROR_MISSINGPARAMS = "Missing parameters required for this operation.";

	public static final int ACTION_LIST = 0;
	public static final int ACTION_READ = 1;
	public static final int ACTION_CREATE = 2;
	public static final int ACTION_UPDATE = 3;
	public static final int ACTION_DELETE = 4;

	public static final String PREFS_NAME = "API";
	public static final String TAG = "com.symmetric.api";
	public static final String DATEFORMAT = "yyyy-MM-dd'T'HH:mm:ssZZZZZ";

	// Configuration and Setup
	static String appPackage;
	static File appDirectory;
	static Context appContext;
	private static HashMap<String, String> configuration = new HashMap<String, String>();

	// Required model methods
	public interface JSONSerializable
	{
		public JSONObject getJSONObject();
	}

	static
	{
		configuration.put(CONFIG_CURRENT_USER_URL, "/api/me");
		configuration.put(CONFIG_LOGIN_URL, "/api/login");
		configuration.put(CONFIG_LOGOUT_URL, "/api/logout");
		configuration.put(CONFIG_HTTPS_LOGIN, "true");
	}

	private API() {}

	public static void setup(Context context)
	{
		appPackage = context.getPackageName();
		appDirectory = context.getFilesDir();
		appContext = context.getApplicationContext();
	}

	public static void setConfiguration(String key, String value)
	{
		configuration.put(key, value);
	}

	public static String getConfiguration(String key)
	{
		return configuration.get(key);
	}

	private static Handler mainHandler = new Handler(Looper.getMainLooper());

	public static void runOnUiThread(Runnable action)
	{
		if(mainHandler.getLooper() == Looper.myLooper())
			action.run();
		else
			mainHandler.post(action);
	}

	public static boolean isUiThread()
	{
		return (mainHandler.getLooper() == Looper.myLooper());
	}

	public static boolean isConnected()
	{
		ConnectivityManager connectivityManager;
		connectivityManager = (ConnectivityManager)appContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		if(connectivityManager == null)
			return false;
		return (connectivityManager.getActiveNetworkInfo() != null && connectivityManager.getActiveNetworkInfo().isConnected() == true);
	}

	public static String convertStreamToString(InputStream is)
	{
		try {
			return new Scanner(is).useDelimiter("\\A").next();
		} catch (java.util.NoSuchElementException e) {
			return "";
		}
	}

	public static int parseInt(String intString)
	{
		try {
			return Integer.parseInt(intString);
		} catch(Exception e) {
			return 0;
		}
	}

	public static Date parseDate(String dateString)
	{
		Date date = null;
		if(dateString == null || dateString.length() == 0)
			return null;
		try
		{
			SimpleDateFormat formatter = new SimpleDateFormat(DATEFORMAT);
			formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
			// Parse the date removing fractional seconds if present and replacing Z with a numerical offset
			if(dateString.length() >= 23 && dateString.charAt(19) == '.')
				dateString = dateString.substring(0, 19) + dateString.substring(23);
			if(dateString.endsWith("Z"))
				dateString = dateString.substring(0, dateString.length() - 1) + "+00:00";
			date = formatter.parse(dateString);
		} catch (ParseException e)
		{
			Log.e(TAG, e.getMessage(), e);
		}
		return date;
	}

	public static String formatDate(Date date)
	{
		if(date == null)
			return null;
		SimpleDateFormat formatter = new SimpleDateFormat(DATEFORMAT);
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		return formatter.format(date);
	}

	public static boolean verifyEmail(String email)
	{
		if(email.indexOf('@') != -1)
		{
			String[] components = email.split("@");
			String user;
			String host;

			if(components.length != 2)
				return false;

			user = components[0];
			host = components[1];

			if(user.length() == 0 || host.length() == 0)
				return false;

			// Verify the host part of the email
			if(host.matches("[^a-zA-Z0-9-.]"))
				return false;

			// Verify the username part of the email
			if(user.matches("[^a-zA-Z0-9-.!#$%&'*+/=?^_`{|}~]"))
				return false;

			return true;
		}
		return false;
	}

	public static String encodeUrlArgs(Object[] args)
	{
		StringBuilder stringBuilder = new StringBuilder();
		String encoded;

		try
		{
			for(int i = 0; i < args.length - 1; i += 2)
			{
				encoded = (args[i+1] != null) ? URLEncoder.encode(args[i+1].toString(), "UTF-8") : "";
				if((i + 2) < args.length - 1)
					stringBuilder.append(String.format("%s=%s&", args[i], encoded));
				else
					stringBuilder.append(String.format("%s=%s", args[i], encoded));
			}
			return stringBuilder.toString();
		} catch(Exception e) { Log.e(TAG, e.getMessage(), e); }
		return null;
	}

	public static String byteArrayToString(byte[] ba)
	{
		StringBuilder hex = new StringBuilder(ba.length * 2);
		for(int i = 0; i < ba.length; ++i)
			hex.append(String.format("%02x", ba[i]));
		return hex.toString();
	}

	public static byte[] stringToByteArray(String hs)
	{
		byte[] ba = new byte[hs.length()/2];
		for(int i = 0; i < hs.length(); i += 2)
			ba[i/2] = (byte)Integer.parseInt(hs.substring(i, i + 2), 16);
		return ba;
	}

	private static final String CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding";

	/** Function to encrypt persisted data. Returns the original plain text if not successful. */
	public static String encrypt(String plainText)
	{
		SecretKeySpec key;
		Cipher cipher;
		String cipherText = plainText;
		int padLength;

		try
		{
			// Pad the plain text first
			padLength = 16 - (plainText.length() % 16);
			if(padLength > 0)
			{
				Random rand = new Random();
				StringBuilder padding = new StringBuilder();
				padding.append('\0');
				for(int i = 0; i < padLength - 1; ++i)
					padding.append((char)(rand.nextInt(26) + 'a'));
				plainText += padding.toString();
			}
			key = new SecretKeySpec(API.getConfiguration(CONFIG_MOBILE_KEY).substring(0, 32).getBytes(), CIPHER_TRANSFORMATION);
			cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, key);
			cipherText = byteArrayToString(cipher.doFinal(plainText.getBytes()));
		} catch(Exception e) { Log.e(TAG, "Encrypt failed with error: " + e.getMessage()); }
		return cipherText;
	}

	/** Function to decrypt persisted data. Returns the original cipherText if not successful. */
	public static String decrypt(String cipherText)
	{
		SecretKeySpec key;
		Cipher cipher;
		String plainText = cipherText;

		try
		{
			key = new SecretKeySpec(API.getConfiguration(CONFIG_MOBILE_KEY).substring(0, 32).getBytes(), CIPHER_TRANSFORMATION);
			cipher = Cipher.getInstance(CIPHER_TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, key);
			plainText = new String(cipher.doFinal(stringToByteArray(cipherText)));
			if(plainText.indexOf('\0') != -1)
				plainText = plainText.split("\0")[0];
		} catch(Exception e) { Log.e(TAG, "Decrypt failed with error: " + e.getMessage()); }
		return plainText;
	}
}
