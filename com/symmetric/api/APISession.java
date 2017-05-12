package com.symmetric.api;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * APISession will automatically renew sessions with the stored credentials.
 * Upon renewal, no STARTED notification is sent, but an ENDED notifiation will be sent if the renewal fails
 * To add login or facebook credentials to existing account, set the password, or set the facebook id via the user info and then login with new credentials */
public class APISession
{
	public static final String ACTION_SESSION_STARTED = "symmetric.action.ACTION_SESSION_STARTED";
	public static final String ACTION_SESSION_ENDED = "symmetric.action.ACTION_SESSION_ENDED";
	public static final String ACTION_SESSION_FAILED = "symmetric.action.ACTION_SESSION_FAILED";
	public static final String EXTRA_USER_ID = "USER_ID";
	public static final String EXTRA_PREVIOUS_USER_ID = "PREVIOUS_USER_ID";
	public static final String EXTRA_CREDENTIAL_TYPE = "CREDENTIAL_TYPE";

	static final String COOKIE_SESSION_ID = "sessionid";
	static final String COOKIE_CSRF_TOKEN = "csrftoken";

	private static final String SETTING_SESSION_ID = "SESSION_ID";
	private static final String SETTING_SESSION_CSRF_TOKEN = "SESSION_CSRF_TOKEN";
	private static final String SETTING_SESSION_USER_ID = "SESSION_USER_ID";
	private static final String SETTING_SESSION_PREV_USER_ID = "SESSION_PREV_USER_ID";

	private static final String HEADER_USER_ID = "X-User-Id";
	private static final String HEADER_SET_COOKIE = "Set-Cookie";

	private static final String PATH_CREDENTIALS = "api_credentials.dat";

	Credentials credentials;
	int userId;
	String sessionid;
	String csrfToken;

	private static APISession sharedInstance;
	private int prevUserId;
	private boolean loginInProgress;
	private ArrayList<APIURLConnection> waitingConnections;

	public interface Credentials extends Externalizable
	{
		public int getType();
		public JSONObject getJSONObject();
		public JSONObject getJSONObjectForAuthenticationChallenge(APIURLConnection connection);
	}

	private APISession()
	{
		load();
	}

	public static APISession getSharedSession()
	{
		if(sharedInstance == null)
			sharedInstance = new APISession();
		return sharedInstance;
	}

	public boolean isLoggedIn()
	{
		return this.sessionid != null && this.sessionid.length() > 0;
	}

	public Credentials getCredentials()
	{
		return this.credentials;
	}

	public int getUserId()
	{
		return this.userId;
	}

	public String getSessionid()
	{
		return this.sessionid;
	}

	public String getCsrfToken()
	{
		return this.csrfToken;
	}

	private void loginFailed(String error)
	{
		final Intent intent;
		boolean renewal = (this.userId != 0);

		this.prevUserId = this.userId;
		this.userId = 0;
		this.credentials = null;
		this.loginInProgress = false;

		if(!API.isConnected())
			error = API.ERROR_NOINTERNET;
		else if(error == null)
			error = API.ERROR_BADCONNECTION;
		if(renewal)
		{
			save();
			intent = new Intent(ACTION_SESSION_ENDED);
		}
		else
		{
			intent = new Intent(ACTION_SESSION_FAILED);
		}
		intent.putExtra(API.EXTRA_ERROR, error);
		API.runOnUiThread(new Runnable() { public void run() { LocalBroadcastManager.getInstance(API.appContext).sendBroadcast(intent); } });
	}

	public void loginWithCredentials(final Credentials credentials)
	{
		if(credentials == null || isLoggedIn() || this.loginInProgress)
			return;

		this.loginInProgress = true;
		this.credentials = credentials;
		Thread thread = new Thread(new Runnable() {
			public void run()
			{
				byte[] data;
				APIURLConnection apiConnection = null;
				HttpURLConnection connection = null;
				OutputStream out;
				try
				{
					data = credentials.getJSONObject().toString().getBytes();
					apiConnection = new APIURLConnection(API.ACTION_CREATE, API.getConfiguration(API.CONFIG_LOGIN_URL), null, data, Boolean.parseBoolean(API.getConfiguration(API.CONFIG_HTTPS_LOGIN)), false, false);
					apiConnection.newConnection();
					connection = apiConnection.connection;
					if(data != null && data.length > 0)
					{
						connection.setDoOutput(true);
						connection.setFixedLengthStreamingMode(data.length);
						out = connection.getOutputStream();
						out.write(data);
						out.close();
					}
					if(connection.getResponseCode() == 401)
					{
						connection.disconnect();
						data = APISession.this.credentials.getJSONObjectForAuthenticationChallenge(apiConnection).toString().getBytes();
						apiConnection = new APIURLConnection(API.ACTION_CREATE, API.getConfiguration(API.CONFIG_LOGIN_URL), null, data, Boolean.parseBoolean(API.getConfiguration(API.CONFIG_HTTPS_LOGIN)), false, false);
						apiConnection.newConnection();
						connection = apiConnection.connection;
						if(data != null && data.length > 0)
						{
							connection.setDoOutput(true);
							connection.setFixedLengthStreamingMode(data.length);
							out = connection.getOutputStream();
							out.write(data);
							out.close();
						}
					}
					// Process the response, either from a first or challenge connection
					if(connection.getResponseCode() >= 400)
					{
						String error = null;
						try
						{
							error = new JSONObject(API.convertStreamToString(connection.getErrorStream())).getString("message");
						}
						catch(JSONException e) { }
						APISession.this.loginFailed(error);
					}
					else
					{
						int newUserId = 0;
						String value;
						Scanner scanner;
						List<String> sessionCookies = connection.getHeaderFields().get(HEADER_SET_COOKIE);

						APISession.this.sessionid = null;
						APISession.this.csrfToken = null;
						if(sessionCookies != null)
						{
							for(String sessionCookie : sessionCookies)
							{
								if(sessionCookie != null && sessionCookie.length() > 0)
								{
									try
									{
										scanner = new Scanner(sessionCookie);
										scanner.skip("\\s*" + COOKIE_SESSION_ID + "=");
										scanner.useDelimiter(";");
										value = scanner.next();
										if(value != null && value.length() > 0)
											APISession.this.sessionid = value;
									} catch(NoSuchElementException e) { }
									try
									{
										scanner = new Scanner(sessionCookie);
										scanner.skip("\\s*" + COOKIE_CSRF_TOKEN + "=");
										scanner.useDelimiter(";");
										value = scanner.next();
										if(value != null && value.length() > 0)
											APISession.this.csrfToken = value;
									} catch(NoSuchElementException e) { }
								}
							}
						}
						value = connection.getHeaderField(HEADER_USER_ID);
						if(value != null)
							newUserId = Integer.parseInt(value);

						// CSRF token can be optional, but sessionid and user id aren't
						if(APISession.this.sessionid == null || APISession.this.sessionid.length() == 0 || newUserId == 0)
						{
							APISession.this.loginFailed(null);
						}
						else
						{
							APISession.this.prevUserId = APISession.this.userId;
							APISession.this.userId = newUserId;
							APISession.this.save();
							// Only notify if this was not a session getting renewed
							if(APISession.this.prevUserId == 0)
							{
								API.runOnUiThread(new Runnable() {
									public void run()
									{
										Intent intent = new Intent(ACTION_SESSION_STARTED);
										intent.putExtra(EXTRA_USER_ID, APISession.this.userId);
										intent.putExtra(EXTRA_PREVIOUS_USER_ID, APISession.this.prevUserId);
										intent.putExtra(EXTRA_CREDENTIAL_TYPE, APISession.this.credentials.getType());
										LocalBroadcastManager.getInstance(API.appContext).sendBroadcast(intent);
									}
								});
							}
						}
					}
				}
				catch(IOException e)
				{
					APISession.this.loginFailed(e.getMessage());
				}
				finally
				{
					APISession.this.loginInProgress = false;
					if(apiConnection.connection != null)
						apiConnection.connection.disconnect();
				}
			}
		});
		thread.start();
	}

	public void logout()
	{
		if(!isLoggedIn() || this.loginInProgress)
			return;

		this.prevUserId = this.userId;
		this.userId = 0;
		this.sessionid = null;
		this.csrfToken = null;
		this.credentials = null;
		save();

		// Post the session ended notification
		LocalBroadcastManager.getInstance(API.appContext).sendBroadcast(new Intent(ACTION_SESSION_ENDED));

		// Call the logout URL and ignore the result, it doesn't matter if the server actually logs out out the user
		Thread thread = new Thread(new Runnable() {
			public void run()
			{
				try
				{
					APIURLConnection connection = new APIURLConnection(API.ACTION_READ, API.getConfiguration(API.CONFIG_LOGOUT_URL), null, null, Boolean.parseBoolean(API.getConfiguration(API.CONFIG_HTTPS_LOGIN)), false, false);
					connection.execute();
				} catch(IOException e) { }
			}
		});
		thread.start();
	}

	boolean waitForLogin(APIURLConnection connection)
	{
		if(this.loginInProgress)
		{
			if(this.waitingConnections == null)
				this.waitingConnections = new ArrayList<APIURLConnection>();
			this.waitingConnections.add(connection);
		}
		return this.loginInProgress;
	}

	boolean processResponse(APIURLConnection connection) throws IOException
	{
		if(connection.getResponseCode() == 401)
		{
			if(this.waitingConnections == null)
				this.waitingConnections = new ArrayList<APIURLConnection>();
			this.waitingConnections.add(connection);
			// Renew the session
			this.sessionid = null;
			this.csrfToken = null;
			loginWithCredentials(this.credentials);
			return false;
		}
		return true;
	}

	private void save()
	{
		SharedPreferences settings = API.appContext.getSharedPreferences(API.PREFS_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();

		if(this.sessionid != null)
			editor.putString(SETTING_SESSION_ID, this.sessionid);
		else
			editor.remove(SETTING_SESSION_ID);

		if(this.csrfToken != null)
			editor.putString(SETTING_SESSION_CSRF_TOKEN, this.csrfToken);
		else
			editor.remove(SETTING_SESSION_CSRF_TOKEN);

		if(this.userId != 0)
			editor.putInt(SETTING_SESSION_USER_ID, this.userId);
		else
			editor.remove(SETTING_SESSION_USER_ID);

		if(this.prevUserId != 0)
			editor.putInt(SETTING_SESSION_PREV_USER_ID, this.prevUserId);
		else
			editor.remove(SETTING_SESSION_PREV_USER_ID);

		editor.commit();

		if(this.credentials != null)
		{
			ObjectOutputStream out = null;
			try
			{
				out = new ObjectOutputStream(API.appContext.openFileOutput(PATH_CREDENTIALS, Context.MODE_PRIVATE));
				out.writeObject(this.credentials);
			} catch(Exception e) { }
			finally
			{
				try
				{
					if(out != null)
						out.close();
				} catch(IOException e) { }
			}
		}
		else
		{
			API.appContext.deleteFile(PATH_CREDENTIALS);
		}
	}

	private void load()
	{
		SharedPreferences settings = API.appContext.getSharedPreferences(API.PREFS_NAME, Context.MODE_PRIVATE);
		this.sessionid = settings.getString(SETTING_SESSION_ID, null);
		if(this.sessionid != null)
		{
			this.csrfToken = settings.getString(SETTING_SESSION_CSRF_TOKEN, null);
			this.userId = settings.getInt(SETTING_SESSION_USER_ID, 0);
			this.prevUserId = settings.getInt(SETTING_SESSION_PREV_USER_ID, 0);
		}

		ObjectInputStream in = null;
		try
		{
			in = new ObjectInputStream(API.appContext.openFileInput(PATH_CREDENTIALS));
			this.credentials = (Credentials)in.readObject();
		} catch(Exception e) { }
		finally
		{
			try
			{
				if(in != null)
					in.close();
			} catch(IOException e) { }
		}
	}
}
