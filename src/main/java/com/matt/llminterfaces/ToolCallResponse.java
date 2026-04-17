package com.matt.llminterfaces;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class ToolCallResponse extends ResponseItem {

	String textContent;
	BinaryContent binaryContent;
	JSONObject jsonResponse;
	ToolCallRequest toolCallRequest;
	
	public ToolCallResponse () {
	}	
	
	public ToolCallResponse (ToolCallRequest request) {
		this();
		this.toolCallRequest = request;
	}
	
	public abstract ToolCallResponse fromJSON (JSONObject toolCall);
	
	public abstract JSONObject toJSON() throws JSONException;
	
	public abstract JSONObject toJSON(boolean includeBinary) throws JSONException;

	public String getTextContent() {
		return textContent;
	}

	public void setTextContent(String textContent) {
		this.textContent = textContent;
	}

	public BinaryContent getBinaryContent() {
		return binaryContent;
	}

	public void setBinaryContent(BinaryContent binaryContent) {
		this.binaryContent = binaryContent;
	}

	public JSONObject getJsonResponse() {
		return jsonResponse;
	}

	public void setJsonResponse(JSONObject jsonResponse) {
		this.jsonResponse = jsonResponse;
	}

	public ToolCallRequest getToolCallRequest() {
		return toolCallRequest;
	}

	public void setToolCallRequest(ToolCallRequest toolCallRequest) {
		this.toolCallRequest = toolCallRequest;
	}
	
	



}
