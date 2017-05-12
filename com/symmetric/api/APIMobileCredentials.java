package com.symmetric.api;

import android.content.Context;
import android.provider.Settings.Secure;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.SecurityException;
import java.security.MessageDigest;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

public class APIMobileCredentials implements APISession.Credentials, Externalizable
{
	private static final String XHEADER_AUTHMOBILE_TOKEN = "X-Authmobile-Token";
	private static final String XHEADER_AUTHMOBILE_NEW_UUID = "X-Authmobile-New-Uuid";

	public static final int TYPE = 102;

	public String token;
	public String uuid;
	private boolean newUuid;

	public int getType()
	{
		return TYPE;
	}

	public JSONObject getJSONObject()
	{
		String device, hashStr = null;
		JSONObject jsonObject = new JSONObject();

		if(this.uuid == null || this.uuid.length() == 0 || this.token == null || this.token.length() == 0)
		{
			try
			{
				jsonObject.put("uuid", 0);
				jsonObject.put("hash", 0);
			} catch(JSONException e) {}
			return jsonObject;
		}

		device = getDeviceId();
		try
		{
			MessageDigest md = MessageDigest.getInstance("SHA1");
			md.update(this.uuid.getBytes());
			md.update(device.getBytes());
			md.update(this.token.getBytes());
			md.update(API.getConfiguration(API.CONFIG_MOBILE_KEY).getBytes());
			hashStr = API.byteArrayToString(md.digest());
		}
		catch(Exception e)
		{
			Log.e(API.TAG, e.getMessage());
		}

		try
		{
			jsonObject.put("uuid", this.uuid);
			jsonObject.put("hash", hashStr);
			if(this.newUuid)
				jsonObject.put("device", getDeviceId());
		} catch(JSONException e) { }
		return jsonObject;
	}

	public JSONObject getJSONObjectForAuthenticationChallenge(APIURLConnection connection)
	{
		this.token = connection.getHeaderField(XHEADER_AUTHMOBILE_TOKEN);
		if(this.token == null)
			return null;
		newUuid = false;
		if(this.uuid.length() == 0)
		{
			this.uuid = connection.getHeaderField(XHEADER_AUTHMOBILE_NEW_UUID);
			if(this.uuid == null)
				return null;
			this.newUuid = true;
		}
		if(this.token.length() == 0 || this.uuid.length() == 0)
			return null;
		else
			return getJSONObject();
	}

	public void readExternal(ObjectInput input) throws IOException, ClassNotFoundException
	{
		this.token = (String)input.readObject();
		this.uuid = (String)input.readObject();
	}

	public void writeExternal(ObjectOutput output) throws IOException
	{
		output.writeObject(this.token);
		output.writeObject(this.uuid);
	}

	private static String getDeviceId()
	{
		String udid = null;
		TelephonyManager tm;

		// Get the device ID
		tm = (TelephonyManager)API.appContext.getSystemService(Context.TELEPHONY_SERVICE);
		if(tm != null)
		{
			try
			{
				udid = tm.getDeviceId();
			}
			catch (SecurityException e) { }
			tm = null;
		}
		if(((udid == null) || (udid.length() == 0)) && (API.appContext != null))
		{
			udid = Secure.getString(API.appContext.getContentResolver(), Secure.ANDROID_ID);
		}
		if((udid == null) || (udid.length() == 0))
		{
			// Last resort, if still null, then generate a uuid on the client
			udid = UUID.randomUUID().toString();
		}

		return udid;
	}
}
