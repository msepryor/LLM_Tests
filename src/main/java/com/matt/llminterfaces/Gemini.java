package com.matt.llminterfaces;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import com.matt.llminterfaces.APIKey.Model;
import com.matt.llminterfaces.Tool.Parameter;

/**
 * Gemini-specific implementation of {@link LLMModel}.
 *
 * Translates the framework's internal request/response abstractions into the
 * JSON schema expected by the Gemini Messages API, including support for:
 * system prompts, tool definitions, tool calls, tool results, text responses,
 * image inputs, and persisted conversation history.
 * 
 * @author Matt Pryor
 */

public class Gemini extends LLMModel {

	public Gemini() {
		super(Model.GEMINI);
	}

	public void loadPromptsEtc(Document configDoc) throws IOException {
		Document doc = configDoc;
		Element root = (Element) doc.getElementsByTagName("llm-config").item(0);

		setModelName(root.getAttribute("modelname"));
		setEndPoint(root.getAttribute("endpoint"));
		setApiVersion(root.getAttribute("apiversion"));
		try {
			setDefaultTemperature(Float.parseFloat(root.getAttribute("temperature")));
		} catch (NumberFormatException ex) {
			setDefaultTemperature(0.0F);
		}

		try {
			setMaxResponseTokens(Integer.parseInt(root.getAttribute("maxresponsetokens")));
		} catch (NumberFormatException ex) {
			setMaxResponseTokens(1024);
		}

		NodeList nl = doc.getElementsByTagName("user-prompt-append");
		if (nl != null && nl.getLength() > 0) {
			Element reminderE = (Element) nl.item(0);
			StringBuffer rem = new StringBuffer();
			NodeList cnl = reminderE.getChildNodes();
			for (int x = 0; x < cnl.getLength(); x++) {
				Node n = (Node) cnl.item(x);
				if (n instanceof Text) {
					rem.append(((Text) n).getData());
				}
			}
			setUserPromptAppend(rem.toString());
		}
		nl = doc.getElementsByTagName("sysprompt");
		for (int i = 0; i < nl.getLength(); i++) {
			StringBuffer sbuff = new StringBuffer();
			Element e = (Element) nl.item(i);
			NodeList cnl = e.getChildNodes();
			for (int x = 0; x < cnl.getLength(); x++) {
				Node n = (Node) cnl.item(x);
				if (n instanceof Text) {
					sbuff.append(((Text) n).getData());
				}
			}
			boolean cache = "true".equals(e.getAttribute("cache").toLowerCase());
			String prompt = sbuff.toString();
			addSystemPrompt(prompt, cache);
		}
	}
	
	protected JSONArray availableToolsToJSON () throws JSONException {
		if (getAvailableTools().size() > 0) {
			JSONArray array = new JSONArray();
			for (Tool tool : getAvailableTools()) {
				JSONObject toolObj = new JSONObject();
				toolObj.put("name", tool.getToolName());
				toolObj.put("description", tool.getToolDescription());
				JSONObject parameters = new JSONObject();
				parameters.put("type", "OBJECT");
				toolObj.put("parameters", parameters);
				JSONObject properties = new JSONObject();
				for (Tool.Parameter p : tool.getParameters()) {
					JSONObject property = new JSONObject();
					property.put("type", p.getType().getTypeString());
					if (p.getType().equals(Tool.Parameter.DataType.ARRAY)) {
						JSONObject items = new JSONObject();
						items.put("type", "string");
						property.put("items", items);
					}
					properties.put(p.getKey(), property);
				}
				parameters.put("properties", properties);
				array.put(toolObj);
			}
			
			return array;
		}	
		return null;
	}


	protected List <ResponseItem> infer(List <ResponseItem> userRequest, int maxTokens, float temperature) throws InferenceException {
        String jsonResponse = null;
		try {
			StringBuffer sbPrompts = new StringBuffer();
			
			for (Iterator <SystemPrompt> i = getPromptItems().iterator(); i.hasNext();) {
				SystemPrompt sysPrompt = i.next();
				sbPrompts.append (sysPrompt.getPromptText()+"\n\n");

			}		
			
			JSONArray contents = new JSONArray();
			
			for (MessagePair mp : getMessagePairs()) {
				List <ResponseItem> userMessages = (List <ResponseItem>) mp.getUserMessage();
				List <ResponseItem> systemMessages = (List <ResponseItem>) mp.getSystemMessage();
				
				JSONObject user = new JSONObject();
				user.put("role", "user");
				JSONArray content = new JSONArray();
				for (ResponseItem i : userMessages) {
					if (!(i instanceof ImageResponse)) {
						content.put(i.toJSON(isStoreBinaryDataInHistory()));
					}
				}
				user.put("parts", content);

				JSONObject assistant = new JSONObject();
				assistant.put("role","model");
				content = new JSONArray();
				for (ResponseItem i : systemMessages) {
					content.put(i.toJSON());
				}
				assistant.put("parts", content);
				
				contents.put(user);
				contents.put(assistant);
			}

			JSONObject user = new JSONObject();
			user.put("role", "user");
			JSONArray parts = new JSONArray();
			for (ResponseItem i : userRequest) {
				if (i instanceof ImageResponse) {
					ImageResponse ir = (ImageResponse) i;
					if (ir.getAccompanyingInputText() != null) {
						JSONObject textPart = new JSONObject();
						textPart.put("text", ((ImageResponse) i).getAccompanyingInputText());
						parts.put(textPart);
					}
					parts.put(i.toJSON());
				} else {
					JSONObject o = i.toJSON(isStoreBinaryDataInHistory());
					parts.put(o);
				}
			}
			user.put("parts", parts);
			contents.put(user);
			
			JSONObject requestJSON = new JSONObject();
			JSONObject systemInstruction = new JSONObject();
			JSONObject sysInstParts = new JSONObject();
			sysInstParts.put("text", sbPrompts.toString());
			systemInstruction.put("parts", sysInstParts);
			requestJSON.put("system_instruction", systemInstruction);
			
			requestJSON.put("contents", contents);
			
			JSONObject config = new JSONObject();
			config.put("temperature", temperature);
			config.put("maxOutputTokens", maxTokens);
			requestJSON.put("generationConfig", config);
			
			if (getAvailableTools().size() > 0) {
				JSONObject toolsObj = new JSONObject();
				JSONArray toolsArray = availableToolsToJSON();
				toolsObj.put("functionDeclarations", toolsArray);
				requestJSON.put ("tools", toolsObj);
			}
		
			
			String data = requestJSON.toString(3);

			logRequest(data);
			
			URL u = new URL(getEndPoint()+getModelName()+":generateContent?key="+getApiKey());
			HttpURLConnection c = (HttpURLConnection) u.openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty ("content-type", "application/json");
			
			int l = data.length();
			c.setRequestProperty("Content-Length" , ""+l);
			c.setDoOutput(true);
			
			OutputStreamWriter wr = new OutputStreamWriter(c.getOutputStream(), StandardCharsets.UTF_8);
	        wr.write(data.toString());
	        wr.flush();
	        InputStream is = null;

	        try {
	        	is = c.getInputStream();
	        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        	byte [] buff = new byte [1024];
	        	int n = -1;
	        	while ((n = is.read(buff)) != -1) {
	        		baos.write (buff, 0, n);
	        	}
	        	String json = new String (baos.toByteArray(), StandardCharsets.UTF_8);
	        	jsonResponse = json;
	        	JSONObject o = new JSONObject(json);
				logResponse(o.toString(3));
	        	JSONArray candidates = o.getJSONArray("candidates");
	        	JSONObject c1 = candidates.getJSONObject(0);
	        	JSONObject content = c1.getJSONObject("content");
	        	JSONArray responseParts = content.getJSONArray("parts");
	        	
	        	List <ResponseItem> replyList = new ArrayList <ResponseItem> ();
	        	
	        	for (int x=0; x < responseParts.length(); x++) {
	        		JSONObject jsono = responseParts.getJSONObject(x);
	        		if (jsono.has("text")) {
	        			ResponseItem i = new GeminiTextResponse(jsono.getString("text"));
	        			replyList.add(i);
	        		}
	        		if (jsono.has("functionCall")) {
	        			GeminiToolCallRequest i = parseToolCall(jsono.getJSONObject("functionCall"));
	        			if (jsono.has("thoughtSignature")) {
	        				i.setThoughtSignature(jsono.getString("thoughtSignature"));
	        			}
	        			replyList.add(i);
	        		}
	        		
	        	}	
	        	
	        	String stopReason = c1.getString("finishReason");
				if ("MAX_TOKENS".equals(stopReason)) {
					throw new ResponseTruncatedException(o.toString(5));
				}
				
				return replyList;
	        } catch (IOException ex) {
				if (is != null) {
					try {
						is.close();
					} catch (Exception ex2) {}
				}
	        	is = c.getErrorStream();
	        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
	        	byte [] buff = new byte [1024];
	        	int n = -1;
	        	while ((n = is.read(buff)) != -1) {
	        		baos.write (buff, 0, n);
	        	}
	        	String json = new String (baos.toByteArray(), StandardCharsets.UTF_8);
				logErrorResponse(json);
				JSONObject j = new JSONObject(json);
				InferenceException infEx = null;
				if (j.has("error")) {
					JSONObject error = j.getJSONObject("error");
					String code = error.getString("status");
					if (code.equals("RESOURCE_EXHAUSTED")) {
						infEx = new TooManyRequestsException(error.getString("message"));
					} else {
						infEx = new InferenceException (error.getString("message"));
					}
				}
				infEx.setJsonMessage(j);
				throw infEx;
				
	        } finally {
	        	if (is != null) {
	        		try {is.close();} catch (Exception ex) {}
	        	}
	        }
		} catch (IOException ex) {
			throw new InferenceException("Communication to LLM failed", ex);	
		} catch (JSONException ex) {
			try {
				logErrorResponse(jsonResponse);
			} catch (IOException ex2) {
				System.err.println(jsonResponse);
			}	
			throw new InferenceException("Communication to LLM failed (couldn't parse response)", ex);
		}
	}
	
	public ResponseItem convertUserInputToResponseItem(String input) {
		return new GeminiTextResponse (input);
	}
	

	public class GeminiTextResponse extends ResponseItem implements TextResponse {

		String text;
		
		public GeminiTextResponse (String text) {
			super();
			this.text = text;
		}	
		
		public JSONObject toJSON() throws JSONException {
			JSONObject o = new JSONObject();
			o.put("text", getText());
			return o;
		}

		@Override
		public String getText() {
			return this.text;
		}

		public void setText(String text) {
			this.text = text;
		}
		
	}	
	

	public class GeminiToolCallRequest extends ToolCallRequest {
		
		String thoughtSignature;

		public GeminiToolCallRequest (String toolName) {
			super (null, toolName);
		}
		
		public JSONObject toJSON() throws JSONException {
			JSONObject obj = new JSONObject();
			obj.put("name", getToolName());
			JSONObject input = new JSONObject();
			for (String key : getInputs().keySet()) {
				input.put(key, getInputs().get(key));
			}
			obj.put("args", input);
			JSONObject funccall = new JSONObject ();
			funccall.put("functionCall", obj);
			if (getThoughtSignature() != null) {
				funccall.put("thoughtSignature", getThoughtSignature());
			}
			return funccall;
		}

		public String getThoughtSignature() {
			return thoughtSignature;
		}

		public void setThoughtSignature(String thoughtSignature) {
			this.thoughtSignature = thoughtSignature;
		}
		
	}
	
	public ToolCallResponse createToolCallResponse (ToolCallRequest request) {
		GeminiToolCallResponse r = new GeminiToolCallResponse (request);
		return r;
	}

	public class GeminiToolCallResponse extends ToolCallResponse {
		
		GeminiToolCallRequest request;
		
		public GeminiToolCallResponse (ToolCallRequest request) {
			super ();
			setRequest(request);	
		}	
		
		public JSONObject toJSON() throws JSONException {
			return toJSON(true);
		}
		
		@Override
		public ToolCallResponse fromJSON(JSONObject toolCall) {
			// TODO Auto-generated method stub
			return null;
		}
		
		public JSONObject toJSON(boolean includeBinary) throws JSONException {
			JSONObject o = new JSONObject();
			o.put("name", request.getToolName());
//			if (request.getThoughtSignature() != null) {
//				o.put("thoughtSignature", request.getThoughtSignature());
//			}
			JSONObject responseO = new JSONObject();
			o.put("response", responseO);
			responseO.put("name", request.getToolName());
			if (getTextContent() != null) {
				JSONObject responseContent = new JSONObject();
				responseContent.put("toolResult", getTextContent());
				responseO.put("content", responseContent);
			} else if (getJsonResponse() != null) {
				responseO.put("content", getJsonResponse());
			}
			JSONObject part = new JSONObject();
			part.put("functionResponse", o);
			return part;
		}
		
		@Override
		public void setBinaryContent(BinaryContent binaryContent) {
			throw new IllegalArgumentException("Binary content now allowed in function call response for this model");
		}
		
		public void setTextContent(String textContent) {
			this.textContent = textContent;
		}

		public ToolCallRequest getRequest() {
			return request;
		}

		public void setRequest(ToolCallRequest request) {
			this.request = (GeminiToolCallRequest) request;
		}

	}
	
	GeminiToolCallRequest parseToolCall (JSONObject response_part) throws JSONException {
		String name = response_part.getString("name");
		JSONObject input = response_part.getJSONObject("args");
		Tool tool = getTool(name);
		GeminiToolCallRequest toolCall = new GeminiToolCallRequest(name);
		toolCall.setTool(tool);
		if (tool != null) {
			List <Parameter> params = tool.getParameters();
			for (Parameter param : params) {
				String paramName = param.getKey();
				if (input.has(paramName) && !input.isNull(paramName)) {
					Object value = input.get(paramName);
					// should do some validation here, perhaps.
					toolCall.setInputValue(paramName, value);
				}
			}
		}		
		return toolCall;
	}	

	
	public ImageResponse createImageResponse(byte [] image, String contentType, String fileName, String contentText) {
		ImageResponse res = new GeminiImageResponse();
		res.setBinaryData(image);
		res.setDataType(contentType);
		res.setMediaType("image");
		res.setAccompanyingInputText(contentText);
		return res;
	}
	
	class GeminiImageResponse extends ImageResponse {
		public JSONObject toJSON() throws JSONException {
			JSONObject imagePart = new JSONObject();
			JSONObject inlineData = new JSONObject();
			imagePart.put ("inline_data", inlineData);
			inlineData.put("mime_type", this.getDataType());
			inlineData.put("data", toBase64());
			return imagePart;
		}
	}
	
	@Override
	public void loadHistoryFrom(JSONObject history) {
		
	}
	

}
