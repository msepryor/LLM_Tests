package com.matt.llminterfaces;

import org.json.JSONObject;

public class InferenceException extends Exception {

	JSONObject jsonMessage;
	
	public InferenceException() {
		// TODO Auto-generated constructor stub
	}

	public InferenceException(String message) {
		super(message);
		// TODO Auto-generated constructor stub
	}

	public InferenceException(Throwable cause) {
		super(cause);
		// TODO Auto-generated constructor stub
	}

	public InferenceException(String message, Throwable cause) {
		super(message, cause);
		// TODO Auto-generated constructor stub
	}

	public InferenceException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
		// TODO Auto-generated constructor stub
	}

	public JSONObject getJsonMessage() {
		return jsonMessage;
	}

	public void setJsonMessage(JSONObject jsonMessage) {
		this.jsonMessage = jsonMessage;
	}

}
