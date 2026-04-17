package com.matt.llminterfaces;

import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public abstract class ToolCallRequestArray extends ResponseItem {

	List <ToolCallRequest> toolCalls = new ArrayList <ToolCallRequest> ();
	String content = null;

	public abstract JSONObject toJSON() throws JSONException;

	public List<ToolCallRequest> getToolCalls() {
		return toolCalls;
	}

	public void setToolCalls(List<ToolCallRequest> toolCalls) {
		this.toolCalls = toolCalls;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}
	
	public void addToolCallRequest(ToolCallRequest req) {
		toolCalls.add(req);
	}

}
