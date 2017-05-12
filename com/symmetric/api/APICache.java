package com.symmetric.api;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.Externalizable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public final class APICache extends BroadcastReceiver
{
	private static final String PATH_META_DATA = "api_meta.dat";
	private static final String CACHE_DIRECTORY = "api_cache";
	private static final String SETTING_USERID = "API_CACHE_USER_ID";

	private final class MetaData implements Externalizable
	{
		static final long serialVersionUID = 1677881196793410983L;
		Date created;
		long expiration; // expiration in milliseconds after created
		boolean sessionOnly;

		MetaData(long expiration, boolean sessionOnly)
		{
			this.created = new Date();
			this.expiration = expiration;
			this.sessionOnly = sessionOnly;
		}

		boolean isExpired()
		{
			return (new Date(this.expiration + this.created.getTime())).before(new Date());
		}

		public void readExternal(ObjectInput input) throws IOException, ClassNotFoundException
		{
			this.created = new Date(input.readLong());
			this.expiration = input.readLong();
			this.sessionOnly = input.readBoolean();
		}

		public void writeExternal(ObjectOutput output) throws IOException
		{
			output.writeLong(this.created.getTime());
			output.writeLong(this.expiration);
			output.writeBoolean(this.sessionOnly);
		}
	}

	private static APICache sharedInstance;

	private HashMap<String, Object> cache;
	private HashMap<String, MetaData> meta;
	private int userId;

	private APICache()
	{
		// Initialize and load the in memory cache/data
		this.cache = new HashMap<String, Object>();
		loadMetaData();

		// Register for broadcasts
		LocalBroadcastManager.getInstance(API.appContext).registerReceiver(this, new IntentFilter(APISession.ACTION_SESSION_STARTED));

		// If a session start broadcast was missed because this singleton is created on-demand only, the typical static{} initializer won't work
		// then reset the session cache
		APISession session = APISession.getSharedSession();
		if(session.isLoggedIn() && session.getUserId() != this.userId)
			flushSessionCache();
	}

	public static APICache getSharedCache()
	{
		if(sharedInstance == null)
			sharedInstance = new APICache();
		return sharedInstance;
	}

	public void onReceive(Context context, Intent intent)
	{
		int userId, prevUserId;

		userId = intent.getIntExtra(APISession.EXTRA_USER_ID, 0);
		prevUserId = intent.getIntExtra(APISession.EXTRA_PREVIOUS_USER_ID, 0);
		if(userId != prevUserId)
		{
			flushSessionCache();
			SharedPreferences settings = API.appContext.getSharedPreferences(API.PREFS_NAME, Context.MODE_PRIVATE);
			SharedPreferences.Editor editor = settings.edit();
			this.userId = APISession.getSharedSession().getUserId();
			editor.putInt(SETTING_USERID, this.userId);
			editor.commit();
		}
	}

	private void loadMetaData()
	{
		ObjectInputStream in = null;
		try
		{
			in = new ObjectInputStream(API.appContext.openFileInput(PATH_META_DATA));
			this.meta = (HashMap<String, MetaData>)in.readObject();
		} catch(Exception e) { }
		finally
		{
			try
			{
				if(in != null)
					in.close();
			} catch(IOException e) { }
		}

		SharedPreferences settings = API.appContext.getSharedPreferences(API.PREFS_NAME, Context.MODE_PRIVATE);
		this.userId = settings.getInt(SETTING_USERID, 0);
	}

	private void saveMetaData()
	{
		if(this.meta.size() > 0)
		{
			ObjectOutputStream out = null;
			try
			{
				out = new ObjectOutputStream(API.appContext.openFileOutput(PATH_META_DATA, Context.MODE_PRIVATE));
				out.writeObject(this.meta);
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
			API.appContext.deleteFile(PATH_META_DATA);
		}

		SharedPreferences settings = API.appContext.getSharedPreferences(API.PREFS_NAME, Context.MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt(SETTING_USERID, this.userId);
		editor.commit();
	}

	private File fileForKey(String key)
	{
		return new File(API.appContext.getDir(CACHE_DIRECTORY, Context.MODE_PRIVATE), key + ".dat");
	}

	public void cacheObject(Object obj, String key, long expiration, boolean sessionOnly)
	{
		ObjectOutputStream out = null;

		this.cache.put(key, obj);
		this.meta.put(key, new MetaData(expiration, sessionOnly));
		try
		{
			out = new ObjectOutputStream(new FileOutputStream(fileForKey(key)));
			out.writeObject(obj);
		} catch(Exception e) { }
		finally
		{
			try
			{
				if(out != null)
					out.close();
			} catch(IOException e) { }
		}
		saveMetaData();
	}

	public void cacheCollection(Object[] collection, String key, long expiration, boolean sessionOnly)
	{
		ObjectOutputStream out = null;

		this.cache.put(key, collection);
		this.meta.put(key, new MetaData(expiration, sessionOnly));
		try
		{
			out = new ObjectOutputStream(new FileOutputStream(fileForKey(key)));
			out.writeInt(collection.length);
			for(Object obj : collection)
				out.writeObject(obj);
		} catch(Exception e) { }
		finally
		{
			try
			{
				if(out != null)
					out.close();
			} catch(IOException e) { }
		}
		saveMetaData();
	}

	public Object entryForKey(String key)
	{
		MetaData meta;
		Object obj;
		File file;
		ObjectInputStream in = null;

		meta = this.meta.get(key);
		if(meta != null)
		{
			// Check for expiration, then in memory, and then the disk
			if(meta.isExpired())
			{
				removeEntryForKey(key);
				return null;
			}
			obj = this.cache.get(key);
			if(obj != null)
				return obj;
			file = fileForKey(key);
			if(file.exists())
			{
				try
				{
					in = new ObjectInputStream(new FileInputStream(file));
					obj = in.readObject();
				} catch(Exception e) { }
				finally
				{
					try
					{
						if(in != null)
							in.close();
					} catch(IOException e) { }
				}
				if(obj != null)
					this.cache.put(key, obj);
				return obj;
			}
		}
		return null;
	}

	public Object[] collectionForKey(String key)
	{
		MetaData meta;
		Object[] collection;
		File file;
		ObjectInputStream in = null;
		int size;

		meta = this.meta.get(key);
		if(meta != null)
		{
			// Check for expiration, then in memory, and then the disk
			if(meta.isExpired())
			{
				removeEntryForKey(key);
				return null;
			}
			collection = (Object[])this.cache.get(key);
			if(collection != null)
				return collection;
			file = fileForKey(key);
			if(file.exists())
			{
				try
				{
					in = new ObjectInputStream(new FileInputStream(file));
					size = in.readInt();
					collection = new Object[size];
					for(int i = 0; i < size; ++i)
						collection[i] = in.readObject();
				} catch(Exception e) { }
				finally
				{
					try
					{
						if(in != null)
							in.close();
					} catch(IOException e) { }
				}
				if(collection != null)
					this.cache.put(key, collection);
				return collection;
			}
		}
		return null;
	}

	public void removeEntryForKey(String key)
	{
		this.meta.remove(key);
		this.cache.remove(key);
		fileForKey(key).delete();
		saveMetaData();
	}

	public void flushAll()
	{
		this.cache.clear();
		for(String key : this.meta.keySet())
			fileForKey(key).delete();
		this.meta.clear();
		saveMetaData();
	}

	public void flushExpired()
	{
		String key;
		MetaData meta;

		for(Map.Entry<String, MetaData> entry : this.meta.entrySet())
		{
			key = entry.getKey();
			meta = entry.getValue();
			if(meta.isExpired())
			{
				this.meta.remove(key);
				this.cache.remove(key);
				fileForKey(key).delete();
				saveMetaData();
			}
		}
	}

	public void flushSessionCache()
	{
		String key;
		MetaData meta;

		for(Map.Entry<String, MetaData> entry : this.meta.entrySet())
		{
			key = entry.getKey();
			meta = entry.getValue();
			if(meta.sessionOnly)
			{
				this.meta.remove(key);
				this.cache.remove(key);
				fileForKey(key).delete();
				saveMetaData();
			}
		}
	}
}
