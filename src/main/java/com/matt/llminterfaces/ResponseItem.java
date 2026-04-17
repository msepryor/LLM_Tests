package com.matt.llminterfaces;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class ResponseItem {

	public abstract JSONObject toJSON() throws JSONException;
	
	public JSONObject toJSON (boolean includeBinary) throws JSONException {
		return toJSON();
	}	
	
	
}
