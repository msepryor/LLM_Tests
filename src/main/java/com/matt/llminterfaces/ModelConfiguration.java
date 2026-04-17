package com.matt.llminterfaces;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.matt.llminterfaces.APIKey.Model;

public class ModelConfiguration {

	static Path llmConfigsPath;
	
	static HashMap <Model, ModelConfiguration> configurations = null;
	
	public static  HashMap <Model, ModelConfiguration> getConfigurations() throws IOException, JSONException {
		synchronized (ModelConfiguration.class) {
			if (configurations == null) {
				loadConfigurations();
			}
		}
		return configurations;
	}
	
	public static ModelConfiguration loadConfiguration (Path config, Model model) throws JSONException, IOException {
		llmConfigsPath = config;
		return getConfigurations().get(model);
	}
	
	public static ModelConfiguration loadConfiguration (Model model) throws JSONException, IOException {
		return getConfigurations().get(model);
	}
	
	String defaultEndpoint;
	ArrayList <String> modelVersions;
	Model modelType;
	String iconFile;
	String className;
	String apiVersion; // might not be needed but anyway.
	
	public String getDefaultEndpoint() {
		return defaultEndpoint;
	}
	public void setDefaultEndpoint(String defaultEndpoint) {
		this.defaultEndpoint = defaultEndpoint;
	}
	public ArrayList<String> getModelVersions() {
		return modelVersions;
	}
	public void setModelVersions(ArrayList<String> modelVersions) {
		this.modelVersions = modelVersions;
	}
	public Model getModelType() {
		return modelType;
	}
	public void setModelType(Model modelType) {
		this.modelType = modelType;
	}
	public String getIconFile() {
		return iconFile;
	}
	public void setIconFile(String iconFile) {
		this.iconFile = iconFile;
	}
	
	public String getClassName() {
		return className;
	}
	public void setClassName(String className) {
		this.className = className;
	}
	public String getApiVersion() {
		return apiVersion;
	}
	public void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}
	
/**
	"models": [
		{
			"model": {
				"name" : "CLAUDE",
				"class" : "com.internationalpresence.generative.Claude",
				"endpoint" : "https://api.anthropic.com/v1/messages",
				"model_versions" : [
					{"version" : "claude-3-5-haiku-20241022"},
					{"version" : "claude-3-7-sonnet-20250219"}
				],
				"logo_image" : "claude-ai-icon.png"
			}
		},			
 */
	
	static void loadConfigurations () throws IOException, JSONException {
		configurations = new HashMap <Model, ModelConfiguration> ();
		Path path = llmConfigsPath == null ?  Paths.get("./res/config/llm_settings.json") : llmConfigsPath;
		JSONObject o = new JSONObject (new String (Files.readAllBytes(path), StandardCharsets.UTF_8));
		JSONArray modelsArray = o.getJSONArray("models");
		for (int x=0; x < modelsArray.length(); x++) {
			JSONObject arrObj = modelsArray.getJSONObject(x);
			JSONObject modelObj = arrObj.getJSONObject("model");
			String modelType = modelObj.getString("name");
			String className = modelObj.getString("class");
			String endpoint = modelObj.getString ("endpoint");
			String logo_img = modelObj.getString ("logo_image");
			String api_version = modelObj.getString ("api_version");
			ModelConfiguration conf = new ModelConfiguration();
			conf.setModelType(Model.valueOf(modelType));
			conf.setDefaultEndpoint(endpoint);
			conf.setIconFile(logo_img);
			conf.setClassName(className);
			conf.setApiVersion(api_version);
			ArrayList <String> versions = new ArrayList <String> ();
			JSONArray model_versions = modelObj.getJSONArray("model_versions");
			for (int y=0; y < model_versions.length(); y++) {
				JSONObject v = model_versions.getJSONObject(y);
				String version = v.getString("version");
				versions.add(version);
			}
			conf.setModelVersions(versions);
			configurations.put(conf.getModelType(), conf);
		}	
	}

	private static Path getLlmConfigsPath() {
		return llmConfigsPath;
	}
	
	public static void setLlmConfigsPath(Path llmConfigsPath) {
		ModelConfiguration.llmConfigsPath = llmConfigsPath;
	}



}
