package com.matt.llminterfaces;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;


/**
 * 
 * A utility class for handling tool-call loops and extracting JSON from model response (e.g. if markdown is used).
 * 
 * @author Matt Pryor / Student S24007537
 **/

public abstract class Agent {

	
	
	/**
	 * 
	 * A utility method for handling tool-call loops.
	 * 
	 **/
	
	public String handleToolCallLoop (LLMModel model, List <ResponseItem> requestItems, int maxTokens, float temperature) throws InferenceException {
		boolean complete = false;
		String output = null;
		while (!complete) {
			List <ResponseItem> responses = null;
			boolean retry = false;
			do {
				try {
					responses = model.getModelResponse(requestItems, maxTokens, temperature);
					retry = false;
					try {
						Thread.sleep(5000);
					} catch (InterruptedException ex) {}
				} catch (InferenceException ex) {
					ex.printStackTrace();
					System.out.println("Do you want to retry?");
					BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
					try {
						String input = br.readLine().toLowerCase().trim();
						if (input.equals("y") || input.equals("yes")) {
							retry = true;
						}
					} catch (IOException ex2) {
					}
				}
			} while (retry);
			requestItems.clear();
			boolean toolCall = false;
			for (ResponseItem i : responses) {
				if (i instanceof ToolCallRequest) {
					System.out.println(((ToolCallRequest) i).toJSON().toString(5));
					Tool tool = ((ToolCallRequest) i).getTool();
					tool.getRunner().runTool((ToolCallRequest) i);
					toolCall = true;
				} else if (i instanceof ToolCallRequestArray) {
					for (ToolCallRequest r : ((ToolCallRequestArray) i).getToolCalls()) {
						System.out.println(((ToolCallRequest) r).toJSON().toString(5));
						Tool tool = ((ToolCallRequest) r).getTool();
						tool.getRunner().runTool((ToolCallRequest) r);
						toolCall = true;
					}
				} else if (i instanceof TextResponse) {
					output = ((TextResponse) i).getText();
				}
			}
			if (!toolCall) {
				complete = true;
			} else {
				if (output != null) {
					System.out.println(output);
				}
			}
		}
		try {
			Thread.sleep(5000);
		} catch (InterruptedException ex) {}
		return output;
	}
	
	/**
	 * Extracts structured JSON from TextResponse object. 
	 * @param tr TextResponse
	 * @return JSONObject
	 * @throws JSONException If json cannot be parsed
	 */
	
	public JSONObject extractJSON(TextResponse tr) throws JSONException {
		String t = tr.getText();
		if (!t.startsWith("{") || !t.endsWith("}")) {
			t = t.substring(t.indexOf("{"));
			t = t.substring(0, t.lastIndexOf("}")+1);
		}
		return new JSONObject(t);
	}

}

