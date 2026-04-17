package com.matt.llminterfaces;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.matt.llminterfaces.APIKey.Model;

/**
 * Abstract base class for Large Language Model (LLM) implementations.
 * Provides common functionality for managing system prompts, message history,
 * and API interactions with language models.
 */


public abstract class LLMModel {
		
	Vector <MessagePair> messagePairs;
	Vector <SystemPrompt> systemPromptItems;
	String apiKey;
	String endPoint;
	String modelName;
	String apiVersion;
	String modelDescription;
	Model model;
	String userPromptAppend = null;
	int maxResponseTokens = 1024;
	float defaultTemperature = 0.2F;
	List <Tool> availableTools = new ArrayList<Tool>();
	boolean storeBinaryDataInHistory;
	File configFile;
	
	public LLMModel createClone() throws IOException {
		LLMModel model = loadUserModel (getModel(), getApiKey(), getModelName());
		model.systemPromptItems = new Vector <SystemPrompt> (this.systemPromptItems);
		model.setAvailableTools(new ArrayList <Tool> (this.availableTools));
		model.setStoreBinaryDataInHistory(storeBinaryDataInHistory);
		model.setModelDescription(getModelDescription());
		model.setEndPoint(getEndPoint());
		Vector <MessagePair> messagePairs = new Vector <MessagePair> (getMessagePairs());
		model.setMessagePairs(messagePairs);
		return model;
		
	}
	
	public static LLMModel loadFromConfig (File configFile) throws IOException {
		String parentFileName = configFile.getParentFile().getName();
		
		
		try (FileInputStream fis = new FileInputStream(configFile)) {
			DocumentBuilder builder = DocumentBuilderFactory.newDefaultInstance().newDocumentBuilder();
			Document doc = builder.parse(fis, StandardCharsets.UTF_8.name());
			Element root = (Element) doc.getElementsByTagName("llm-config").item(0);
			String implClassName = root.getAttribute("impl");
			Class<?> rawClass = Class.forName(implClassName);
			if (!LLMModel.class.isAssignableFrom(rawClass)) {
			    throw new ClassCastException(implClassName + " does not implement LLMModel");
			}
			Class <LLMModel> cl = (Class <LLMModel>) Class.forName(implClassName);
			LLMModel loaded = (LLMModel) cl.newInstance();
			loaded.setConfigFile(configFile);
			loaded.loadPromptsEtc(doc);
			loaded.setModelDescription(cl.getSimpleName());
			loaded.setApiKey(APIKey.getInternalAPIKey(loaded.getModel()));
			return loaded;
		} catch (ParserConfigurationException | SAXException | NoKeyAvailableException | InstantiationException | ClassNotFoundException | IllegalAccessException ex) {
			throw new IOException ("Please check the config file '"+configFile.getAbsoluteFile()+" exists and is correctly formed.", ex);			
		}
	}	
	
	public static LLMModel loadUserModel (Model model, String apiKey, String modelVersion) throws IOException {
		ModelConfiguration conf =  ModelConfiguration.loadConfiguration(model);
		return loadUserModel (model, conf.getClassName(), apiKey, conf.getDefaultEndpoint(), modelVersion, conf.getApiVersion());
	}

	
	public static LLMModel loadUserModel (Model model, String className, String apiKey, String endpoint, String modelVersion, String apiVersion) throws IOException {
		try {
			Class<?> rawClass = Class.forName(className);
			if (!LLMModel.class.isAssignableFrom(rawClass)) {
			    throw new ClassCastException(className + " does not implement LLMModel");
			}
			Class <LLMModel> cl = (Class <LLMModel>) Class.forName(className);
			LLMModel loaded = (LLMModel) cl.getConstructor().newInstance();
			loaded.setModel(model);
			loaded.setApiKey(apiKey);
			loaded.setEndPoint(endpoint);
			loaded.setModelName(modelVersion);
			loaded.setApiVersion(apiVersion);
			return loaded;
		} catch (NoSuchMethodException ex) {
			throw new IOException ("Please check the llm settings json file exists and is correctly formed.", ex);			
		} catch (InvocationTargetException ex) {
			throw new IOException ("Please check the llm settings json file exists and is correctly formed.", ex);
		} catch (InstantiationException ex) {
			throw new IOException ("Please check the llm settings json file exists and is correctly formed.", ex);
		} catch (ClassNotFoundException ex) {
			throw new IOException ("Please check the llm settings json file exists and is correctly formed.", ex);
		} catch (IllegalAccessException ex) {
			throw new IOException ("Please check the llm settings json file exists and is correctly formed.", ex);
		}
		
	}
	
	@Override
	public String toString() {
		return getModelDescription()+" "+getModelName();
	}
	
	protected LLMModel(Model model) {
		setModel(model);
		this.messagePairs = new Vector <> ();
		this.systemPromptItems = new Vector <> ();
	}
	
	public abstract void loadPromptsEtc (Document configDoc) throws IOException;
	
	protected abstract JSONArray availableToolsToJSON () throws JSONException;
	
	public void addSystemPrompt (String prompt) {
		this.systemPromptItems.add(new SystemPrompt(prompt));
	}
	
	public void addSystemPrompt (String prompt, boolean cache) {
		this.systemPromptItems.add(new SystemPrompt(prompt, cache));
	}
	
	public void clearMessageHistory() {
		this.messagePairs.clear();
	}
	
	/**
	 * The config file must be a valid XML document of the form:
	 * <llm-config endpoint="?" model="?" apiversion="?">
	 *   <sysprompt>[system prompt part]</sysprompt> - one or many
	 * </llm-config> 
	 * @param configFile
	 */
		
	public static class SystemPrompt {
		String promptText;
		boolean cache;
		public SystemPrompt(String promptText) {
			this.promptText = promptText;
			this.cache = false;
		}
		public SystemPrompt(String promptText, boolean cache) {
			this.promptText = promptText;
			this.cache = cache;			
		}
		public String getPromptText() {
			return promptText;
		}
		public boolean isCache() {
			return cache;
		}
	}
	
	public static class MessagePair {
		
		List <ResponseItem> userMessage;
		List <ResponseItem> systemMessage;
		
		public MessagePair (List <ResponseItem> in, List <ResponseItem> out) {
			this.userMessage = in;
			this.systemMessage = out;
		}	
		
		public List <ResponseItem> getUserMessage() {
			return userMessage;
		}
		public void setUserMessage(List <ResponseItem> userMessage) {
			this.userMessage = userMessage;
		}
		public List <ResponseItem> getSystemMessage() {
			return systemMessage;
		}
		public void setSystemMessage(List <ResponseItem> systemMessage) {
			this.systemMessage = systemMessage;
		}
		
	}
	
	public  String getSimpleModelResponseUsingBuiltinKey (String userRequest) throws InferenceException {
		return getSimpleModelResponseUsingBuiltinKey(userRequest, getMaxResponseTokens(), getDefaultTemperature());
	}
	
	public String getSimpleModelResponseUsingBuiltinKey (String userRequest, int maxTokens) throws InferenceException {
		return getSimpleModelResponseUsingBuiltinKey(userRequest, maxTokens, getDefaultTemperature());
	}
	
	public abstract ResponseItem convertUserInputToResponseItem (String input);

	public String getSimpleModelResponseUsingBuiltinKey (String userRequest, int maxTokens, float temperature) throws InferenceException {
		TextResponse systemResponse = null;
		ResponseItem responseItem = convertUserInputToResponseItem(userRequest);
		ArrayList <ResponseItem> al = new ArrayList <> ();
		al.add(responseItem);
		List <ResponseItem> responseParts = getModelResponse (al, maxTokens, temperature);
		systemResponse = (TextResponse) responseParts.stream()
			    .filter(o -> o instanceof TextResponse)
			    .findFirst()
			    .orElse(null);
		if (systemResponse != null) {
			ArrayList <ResponseItem> input = new ArrayList <ResponseItem>();
			input.add(convertUserInputToResponseItem(userRequest));
			MessagePair mp = new MessagePair (input, responseParts);
			this.messagePairs.add(mp);
			return systemResponse.getText();
		}
		throw new InferenceException ("Model did not return any string content");
	}
	
	public  List <ResponseItem> getModelResponse (List <ResponseItem> userRequest) throws InferenceException {
		return getModelResponse(userRequest, getMaxResponseTokens(), getDefaultTemperature());
	}
	
	public List <ResponseItem> getModelResponse (List <ResponseItem>  userRequest, int maxTokens) throws InferenceException {
		return getModelResponse(userRequest, maxTokens, getDefaultTemperature());
	}

	public List <ResponseItem> getModelResponse (List <ResponseItem> userRequest, int maxTokens, float temperature) throws InferenceException {
		List <ResponseItem> responseParts = null;
		boolean tooManyRequests = false;
		do {
			try {
				responseParts = infer (userRequest, maxTokens, temperature);
				tooManyRequests = false;
			} catch (TooManyRequestsException ex) {
				System.err.println("Rate limit exceeded, pausing for 30 seconds");
				try {
					Thread.sleep(30000L);
				} catch (InterruptedException ex2) {
				}
				tooManyRequests = true;
			}
		} while (tooManyRequests);
		MessagePair mp = new MessagePair (new ArrayList <ResponseItem> (userRequest), responseParts);
		this.messagePairs.add(mp);
		return responseParts;
	}
	
	public String getModelResponse (String textRequest, int maxTokens, float temperature) throws InferenceException {
		ResponseItem responseItem = convertUserInputToResponseItem(textRequest);
		ArrayList <ResponseItem> al = new ArrayList <> ();
		al.add(responseItem);
		List <ResponseItem> responseParts = getModelResponse(al, maxTokens, temperature);
		TextResponse systemResponse = (TextResponse) responseParts.stream()
			    .filter(o -> o instanceof TextResponse)
			    .findFirst()
			    .orElse(null);
		if (systemResponse != null) {
			return ((TextResponse) systemResponse).getText();
		} else {
			throw new InferenceException ("Model didn't return expected response type");
		}
	}
		
	
	protected void logRequest (String request) throws IOException {
		LocalDateTime ldt = LocalDateTime.now();
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");
		File logDir = new File ("./res/logs/llminference/");
		logDir.mkdirs();
		File f = new File (logDir, format.format(ldt)+"_"+getModelName()+"_request.txt");
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream (f);
			PrintStream ps = new PrintStream(fos, true, StandardCharsets.UTF_8.displayName());
			ps.print(request);
			ps.flush();
		} finally {
			if (fos != null) {
				try {fos.close();} catch (Exception ex) {fos = null;}
			}
		}
	}
	
	protected void logResponse(String response) throws IOException {
		LocalDateTime ldt = LocalDateTime.now();
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");
		File logDir = new File ("./res/logs/llminference/");
		logDir.mkdirs();
		File f = new File (logDir, format.format(ldt)+"_"+getModelName()+"_response.txt");

		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream (f);
			PrintStream ps = new PrintStream(fos, true, StandardCharsets.UTF_8.displayName());
			ps.print(response);
		} finally {
			if (fos != null) {
				try {fos.close();} catch (Exception ex) {fos = null;}
			}
		}		
	}
	
	protected void logErrorResponse(String response) throws IOException {
		LocalDateTime ldt = LocalDateTime.now();
		DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmssSSS");
		File logDir = new File ("./res/logs/llminference/");
		logDir.mkdirs();
		File f = new File (logDir, format.format(ldt)+"_"+getModelName()+"_error.txt");
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream (f);
			PrintStream ps = new PrintStream(fos, true, StandardCharsets.UTF_8.displayName());
			ps.print(response);
		} finally {
			if (fos != null) {
				try {fos.close();} catch (Exception ex) {fos = null;}
			}
		}		
	}
	
	public abstract ToolCallResponse createToolCallResponse (ToolCallRequest request);
	
	protected abstract List <ResponseItem> infer (List <ResponseItem> userRequest, int maxTokens, float temperature) throws InferenceException;

	public Vector<MessagePair> getMessagePairs() {
		return messagePairs;
	}

	public void setMessagePairs(Vector<MessagePair> messagePairs) {
		this.messagePairs = messagePairs;
	}

	protected Vector <SystemPrompt> getPromptItems() {
		return systemPromptItems;
	}

	protected String getApiKey() {
		return apiKey;
	}

	protected String getEndPoint() {
		return endPoint;
	}

	public String getModelName() {
		return modelName;
	}

	protected String getApiVersion() {
		return apiVersion;
	}

	public Model getModel() {
		return model;
	}

	protected void setModel(Model model) {
		this.model = model;
	}

	protected void setEndPoint(String endPoint) {
		this.endPoint = endPoint;
	}

	protected void setModelName(String modelName) {
		this.modelName = modelName;
	}

	protected void setApiVersion(String apiVersion) {
		this.apiVersion = apiVersion;
	}

	protected String getUserPromptAppend() {
		return userPromptAppend;
	}

	protected void setUserPromptAppend(String userPromptAppend) {
		this.userPromptAppend = userPromptAppend;
	}

	protected int getMaxResponseTokens() {
		return maxResponseTokens;
	}

	protected void setMaxResponseTokens(int maxResponseTokens) {
		this.maxResponseTokens = maxResponseTokens;
	}

	protected float getDefaultTemperature() {
		return defaultTemperature;
	}

	protected void setDefaultTemperature(float defaultTemperature) {
		this.defaultTemperature = defaultTemperature;
	}

	public List<Tool> getAvailableTools() {
		return availableTools;
	}

	public void setAvailableTools(List<Tool> availableTools) {
		this.availableTools = availableTools;
	}
	
	public void addTool (Tool tool) {
		this.availableTools.add(tool);
	}
	
	public Tool getTool (String key) {
		return getAvailableTools().stream()
			    .filter(t -> t.getToolName().equals(key))
			    .findFirst()
			    .orElse(null);
	}

	public boolean isStoreBinaryDataInHistory() {
		return storeBinaryDataInHistory;
	}

	public void setStoreBinaryDataInHistory(boolean storeBinaryDataInHistory) {
		this.storeBinaryDataInHistory = storeBinaryDataInHistory;
	}	
	
	public ImageResponse createImageResponse(byte [] image, String contentType, String fileName, String contentText) {
		return null;
		// implement this, if the model supports it.
	}

	public File getConfigFile() {
		return configFile;
	}

	public void setConfigFile(File configFile) {
		this.configFile = configFile;
	}

	public String getModelDescription() {
		return modelDescription;
	}

	public void setModelDescription(String modelDescription) {
		this.modelDescription = modelDescription;
	}

	protected void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}
	
	public abstract void loadHistoryFrom (JSONObject history);

}
