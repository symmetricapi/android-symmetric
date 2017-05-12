package com.symmetric.api;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

public class APIUserConnection
{
	//public static final int REQUEST_NONE = 0;
	public static final int REQUEST_FILTER_CONTACTS = 1;
	public static final int REQUEST_CREATE_USER = 2;
	public static final int REQUEST_SET_PASSWORD = 3;
	public static final int REQUEST_RESET_PASSWORD = 4;

	public interface RequestListener
	{
		public void userConnectionDidFilterContacts(APIUserConnection connection, HashMap<String, String> contacts);
		public void userConnectionDidCreateUser(APIUserConnection connection);
		public void userConnectionDidSetPassword(APIUserConnection connection);
		public void userConnectionDidResetPassword(APIUserConnection connection);
		public void requestFailed(APIUserConnection connection, String errorMessage);
	}

	// Base class to make implementing anonymous classes easier
	public static class BasicRequestListener implements RequestListener
	{
		public void userConnectionDidFilterContacts(APIUserConnection connection, HashMap<String, String> contacts) { Log.i(API.TAG, "User connection did filter contacts."); }
		public void userConnectionDidCreateUser(APIUserConnection connection) { Log.i(API.TAG, "Did create user."); }
		public void userConnectionDidSetPassword(APIUserConnection connection) { Log.i(API.TAG, "Did set user password."); }
		public void userConnectionDidResetPassword(APIUserConnection connection) { Log.i(API.TAG, "Did reset user password."); }
		public void requestFailed(APIUserConnection connection, String errorMessage) { Log.e(API.TAG, "User connection failed with error: " + errorMessage); }
	}

	private HttpRequestBase request;
	private int requestType;
	private RequestListener listener;
	private String error;

	public APIUserConnection(RequestListener listener)
	{
		this.listener = listener;
	}

	public void cancel()
	{
		if(this.request != null)
			this.request.abort();
	}

	public int getRequestType()
	{
		return this.requestType;
	}

	public RequestListener getListener()
	{
		return this.listener;
	}

	public String getError()
	{
		return this.error;
	}

	// Currently only supports filtering on one field at a time
	public void filterContacts(HashMap<String, String> contacts)
	{
		return;
	}

	public void filterContacts(HashMap<String, String> contacts, RequestListener listener)
	{
		return;
	}

	public void createUser(String username, String email, String password1, String password2)
	{
		return;
	}

	public void createUser(String username, String email, String password1, String password2, RequestListener listener)
	{
		return;
	}

	// If the current user's session credentials are usename/password, this will change their password
	public void setPassword(String currentPassword, String newPassword1, String newPassword2)
	{
		return;
	}

	public void setPassword(String currentPassword, String newPassword1, String newPassword2, RequestListener listener)
	{
		return;
	}

	// Start the email transaction for a user to reset their password in their web browser
	public void resetPassword(String email)
	{
		return;
	}

	public void resetPassword(String email, RequestListener listener)
	{
		return;
	}
}
