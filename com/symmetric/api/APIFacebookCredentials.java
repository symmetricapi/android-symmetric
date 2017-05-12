package com.symmetric.api;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.json.JSONException;
import org.json.JSONObject;

public class APIFacebookCredentials implements APISession.Credentials, Externalizable
{
	public static final int TYPE = 101;
	public String accessToken;

	public int getType()
	{
		return TYPE;
	}

	public JSONObject getJSONObject()
	{
		JSONObject jsonObject = new JSONObject();
		try
		{
			jsonObject.put("access_token", this.accessToken);
		} catch(JSONException e) { }
		return jsonObject;
	}

	public JSONObject getJSONObjectForAuthenticationChallenge(APIURLConnection connection)
	{
		return null;
	}

	public void readExternal(ObjectInput input) throws IOException, ClassNotFoundException
	{
		this.accessToken = API.decrypt((String)input.readObject());
	}

	public void writeExternal(ObjectOutput output) throws IOException
	{
		output.writeObject(API.encrypt(this.accessToken));
	}
}
