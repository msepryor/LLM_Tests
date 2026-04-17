package com.matt.llminterfaces;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.json.JSONArray;
import org.json.JSONObject;

import com.matt.llminterfaces.APIKey.Model;

/**
 * Utility class for loading config information for an Agent
 * @author Matt Pryor
 */

public class AgentConfig {

	String chatLanguageModel;
	String modelVariant;
	String systemPrompt;
	String endPoint;
	APIKey.Model modelType;
	
	public static AgentConfig getConfig (Class functionHandler)  {
		Path json = Paths.get("./src/main/resources/"+functionHandler.getSimpleName()+".json");
		JSONObject obj = null;
		try {
			obj = new JSONObject(Files.readString(json));
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		String modelVariant = obj.getString("modelVariant");
		Model model = Model.valueOf(obj.getString("model"));
		JSONArray systemPromptArr = obj.getJSONArray("systemPrompt");
		StringBuilder sb = new StringBuilder();
		for (int x=0; x < systemPromptArr.length(); x++) {
			sb.append(systemPromptArr.getString(x));
		}
		String systemPrompt = sb.toString();
		AgentConfig config = new AgentConfig ();
		config.setModelVariant(modelVariant);
		config.setModelType(model);
		config.setSystemPrompt(systemPrompt);
		return config;
		
	}

	public String getChatLanguageModel() {
		return chatLanguageModel;
	}

	public void setChatLanguageModel(String chatLanguageModel) {
		this.chatLanguageModel = chatLanguageModel;
	}

	public String getModelVariant() {
		return modelVariant;
	}

	public void setModelVariant(String modelVariant) {
		this.modelVariant = modelVariant;
	}

	public String getSystemPrompt() {
		return systemPrompt;
	}

	public void setSystemPrompt(String systemPrompt) {
		this.systemPrompt = systemPrompt;
	}

	public String getEndPoint() {
		return endPoint;
	}

	public void setEndPoint(String endPoint) {
		this.endPoint = endPoint;
	}

	public APIKey.Model getModelType() {
		return modelType;
	}

	public void setModelType(APIKey.Model modelType) {
		this.modelType = modelType;
	}
	
}
