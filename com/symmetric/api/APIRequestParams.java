package com.symmetric.api;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import java.util.ArrayList;

public class APIRequestParams implements Parcelable
{
	// Query parameters sent to the server
	public static final String PARAM_QUERY = "q";
	public static final String PARAM_ORDER_BY = "orderby";
	public static final String PARAM_PAGE = "page";
	public static final String PARAM_PAGE_SIZE = "pagesize";
	// XHeaders from the server
	public static final String X_HEADER_TOTAL = "X-Total";
	public static final String X_HEADER_TOTAL_PAGES = "X-Total-Pages";
	public static final String X_HEADER_PAGE = "X-Page";
	public static final String X_HEADER_PAGE_SIZE = "X-Page-Size";

	public String query;
	public String orderBy;
	public int page;
	public int pageSize;
	public int total;
	public int totalPages;

	public APIRequestParams() {}

	public Object[] getArgs()
	{
		ArrayList<Object> args = new ArrayList<Object>();

		if(this.query != null)
		{
			args.add(PARAM_QUERY);
			args.add(this.query);
		}
		if(this.orderBy != null)
		{
			args.add(PARAM_ORDER_BY);
			args.add(this.query);
		}
		if(this.page != 0)
		{
			args.add(PARAM_PAGE);
			args.add(this.page);
		}
		if(this.pageSize != 0)
		{
			args.add(PARAM_PAGE_SIZE);
			args.add(this.pageSize);
		}

		return (Object[])args.toArray(new Object[0]);
	}

	public void processResponse(APIURLConnection connection)
	{
		String header;

		header = connection.getHeaderField(X_HEADER_TOTAL);
		if(header != null)
			this.total = Integer.valueOf(header);

		header = connection.getHeaderField(X_HEADER_TOTAL_PAGES);
		if(header != null)
			this.totalPages = Integer.valueOf(header);

		header = connection.getHeaderField(X_HEADER_PAGE);
		if(header != null)
			this.page = Integer.valueOf(header);

		header = connection.getHeaderField(X_HEADER_PAGE_SIZE);
		if(header != null)
			this.pageSize = Integer.valueOf(header);
	}

	/** Constructor to create a APIRequestParams from a Parcel */
	@SuppressWarnings("unchecked")
	protected APIRequestParams(Parcel in)
	{
		try
		{
			this.query = in.readString();
			this.orderBy = in.readString();
			this.page = in.readInt();
			this.pageSize = in.readInt();
			this.total = in.readInt();
			this.totalPages = in.readInt();
		}
		catch(Exception e) { Log.e(API.TAG, Log.getStackTraceString(e)); }
	}

	/** Implementation of the Parcelable method describeContents. */
	@Override
	public int describeContents()
	{
		return 0;
	}

	/** Implementation of the Parcelable method writeToParcel. */
	@Override
	public void writeToParcel(Parcel dest, int flags)
	{
		dest.writeString(this.query);
		dest.writeString(this.orderBy);
		dest.writeInt(this.page);
		dest.writeInt(this.pageSize);
		dest.writeInt(this.total);
		dest.writeInt(this.totalPages);
	}

	/** Object that is used to regenerate the object. All Parcelables must have a CREATOR that implements these two methods. */
	public static final Parcelable.Creator<APIRequestParams> CREATOR = new Parcelable.Creator<APIRequestParams>() {
		public APIRequestParams createFromParcel(Parcel in) {
			return new APIRequestParams(in);
		}

		public APIRequestParams[] newArray(int size) {
			return new APIRequestParams[size];
		}
	};
}
