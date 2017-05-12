package com.symmetric.api;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.json.JSONException;
import org.json.JSONObject;

public class APILoginCredentials implements APISession.Credentials, Externalizable
{
	public static final int TYPE = 100;
	public String username;
	public String password;

	public int getType()
	{
		return TYPE;
	}

	public JSONObject getJSONObject()
	{
		JSONObject jsonObject = new JSONObject();
		this.username = this.username.toLowerCase();
		try
		{
			jsonObject.put("username", this.username);
			jsonObject.put("password", this.password);
		} catch(JSONException e) { }
		return jsonObject;
	}

	public JSONObject getJSONObjectForAuthenticationChallenge(APIURLConnection connection)
	{
		return null;
	}

	public void readExternal(ObjectInput input) throws IOException, ClassNotFoundException
	{
		this.username = (String)input.readObject();
		this.password = API.decrypt((String)input.readObject());
	}

	public void writeExternal(ObjectOutput output) throws IOException
	{
		output.writeObject(this.username.toLowerCase());
		output.writeObject(API.encrypt(this.password));
	}
}
