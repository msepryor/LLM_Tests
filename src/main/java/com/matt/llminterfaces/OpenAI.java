package com.matt.llminterfaces;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;

import com.matt.llminterfaces.APIKey.Model;
import com.matt.llminterfaces.Tool.Parameter;

/**
 * OpenAI-specific implementation of {@link LLMModel}.
 *
 * Translates the framework's internal request/response abstractions into the
 * JSON schema expected by the OpenAI Messages API, including support for:
 * system prompts, tool definitions, tool calls, tool results, text responses,
 * image inputs, and persisted conversation history.
 * 
 * @author Matt Pryor
 */


public class OpenAI extends LLMModel {

	public OpenAI() {
		super(Model.OPENAI);
	}

	public void loadPromptsEtc (Document configDoc) throws IOException {
		Document doc = configDoc;
		Element root = (Element) doc.getElementsByTagName("llm-config").item(0);
		
		setModelName(root.getAttribute("modelname"));
		setEndPoint(root.getAttribute("endpoint"));
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
			for (int x=0; x < cnl.getLength(); x++) {
				Node n = (Node) cnl.item(x);
				if (n instanceof Text) {
					rem.append(((Text) n).getData());
				}
			}
			setUserPromptAppend(rem.toString());
		}
		nl = doc.getElementsByTagName("sysprompt");
		for (int i=0; i < nl.getLength(); i++) {
			StringBuffer sbuff = new StringBuffer();
			Element e = (Element) nl.item(i);
			NodeList cnl = e.getChildNodes();
			for (int x=0; x < cnl.getLength(); x++) {
				Node n = (Node) cnl.item(x);
				if (n instanceof Text) {
					sbuff.append(((Text) n).getData());
				}
			}
			boolean cache = "true".equals(e.getAttribute("cache").toLowerCase());
			String prompt = sbuff.toString();
			addSystemPrompt (prompt, cache);
		}	

	}

	protected JSONArray availableToolsToJSON() throws JSONException {
		if (getAvailableTools().size() > 0) {
			JSONArray array = new JSONArray();
			for (Tool tool : getAvailableTools()) {
				JSONObject toolObj = new JSONObject(); {
					toolObj.put("type", "function");
					JSONObject function = new JSONObject(); {
						toolObj.put("function", function);
						function.put("name", tool.getToolName());
						function.put("description", tool.getToolDescription());
						function.put("strict", true);
						JSONObject parameters = new JSONObject(); {
							function.put("parameters", parameters);
							parameters.put("type", "object");
							JSONObject properties = new JSONObject(); {
								parameters.put("properties", properties);
								for (Tool.Parameter p : tool.getParameters()) {
									JSONObject property = new JSONObject(); {
										property.put("type", p.getType().getTypeString());
										property.put("description", p.getDescription());
										if (!p.getAcceptableValues().isEmpty()) {
											JSONArray en = new JSONArray();
											for (Object o : p.getAcceptableValues()) {
												en.put(o);
											}
											property.put("enum", en);
										}
					
										properties.put(p.getKey(), property);
									}
								}
							}
						}
						Tool.Parameter[] required = tool.getParameters().stream().filter(Tool.Parameter::isRequired).toArray(Tool.Parameter[]::new);
						if (required.length > 0) {
							JSONArray reqArray = new JSONArray();
							for (Tool.Parameter rp : required) {
								reqArray.put(rp.getKey());
							}
							parameters.put("required", reqArray);
						}
						parameters.put("additionalProperties", false);
					}
				}
				array.put(toolObj);
			}

			return array;
		}
		return null;
	}
	
	protected JSONObject formatRequestJSON (List <ResponseItem> userRequests, int maxTokens, float temperature) throws JSONException {
		JSONObject o = new JSONObject();
		o.put("model", getModelName());
		o.put("max_completion_tokens", maxTokens);
		o.put("temperature", temperature);
		o.put("tools", availableToolsToJSON());
		
		JSONArray messages = new JSONArray();
		for (Iterator <SystemPrompt> i = getPromptItems().iterator(); i.hasNext();) {
			SystemPrompt sp = i.next();
			JSONObject mp = new JSONObject ();
			mp.put("role", "developer");
			mp.put("content", sp.getPromptText());
			messages.put(mp);
		}
		
		for (MessagePair mp : getMessagePairs()) {
			List <ResponseItem> userItems = mp.getUserMessage();
			List <ResponseItem> asstItems = mp.getSystemMessage();
			for (ResponseItem i : userItems) {
				if (!(i instanceof ImageResponse)) {
					JSONObject json = i.toJSON(true);
					if (i instanceof ToolCallResponse) {
						json.put("role","tool");
					} else if (i instanceof ImageResponse) {
						
					} else {
						json.put("role","user");
					}
					messages.put(json);
				}
			}	
			for (ResponseItem i : asstItems) {
				JSONObject json = i.toJSON(true);
				json.put("role","assistant");
				messages.put(json);
			}	

		}
		
		for (ResponseItem ri : userRequests) {
			JSONObject json = ri.toJSON(true);
			json.put("role","user");
			if (ri instanceof ToolCallResponse) {
				json.put("role","tool");
			}
			messages.put(json);
		}
		
		o.put("messages", messages);

		return o;
	}

	protected List<ResponseItem> infer(List<ResponseItem> userRequest, int maxTokens, float temperature) throws InferenceException {
		try {

			JSONObject dataJSON = formatRequestJSON(userRequest, maxTokens, temperature);
			String data = dataJSON.toString(5);
			URL u = new URL(getEndPoint());
			HttpURLConnection c = (HttpURLConnection) u.openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty ("Content-Type", "application/json");
			c.setRequestProperty ("Authorization", "Bearer "+getApiKey());
			logRequest(data);
			
			int l = data.length();
			c.setRequestProperty("Content-Length" , ""+l);
			c.setDoOutput(true);
			
			OutputStreamWriter wr = new OutputStreamWriter(c.getOutputStream());
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
	        	JSONObject o = new JSONObject(json);
				logResponse(o.toString(5));
	        	JSONArray choices = o.getJSONArray("choices");
	        	JSONObject c1 = choices.getJSONObject(0);
	        	String stopReason = c1.getString("finish_reason");
	        	JSONObject msg = c1.getJSONObject("message");
	        	List <ResponseItem> responses = new ArrayList <> ();
	        	String content = null;
				if (msg.has("content")) {
					if (msg.get("content") instanceof String) {
						content = msg.getString("content");
						ResponseItem i = new OpenAITextResponse(content);
						responses.add(i);
					}
				}
				if (msg.has("tool_calls")) {
					JSONArray toolCalls = msg.getJSONArray("tool_calls");
					ToolCallRequestArray arr = new OpenAIToolCallRequestArray();
					for (int x=0; x < toolCalls.length(); x++) {
						JSONObject toolCall = (JSONObject) toolCalls.get(x);
						ToolCallRequest request = parseToolCall(toolCall);
						arr.addToolCallRequest(request);
					}	
					responses.add(arr);
				}
				
				
				if ("length".equals(stopReason)) {
					throw new ResponseTruncatedException(content);
				}

				
				return responses;
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
	        	JSONObject jsono = new JSONObject (json);
				logErrorResponse(jsono.toString(5));
	        	String errorMessage = null;
	        	if (jsono.has("error")) {
	        		JSONObject error = jsono.getJSONObject("error");
	        		if (error.has("code") && error.get("code") instanceof String) {
		        		String code = error.getString("code");
		        		if (code.equals("rate_limit_exceeded")) {
		        			TooManyRequestsException tmrex = new TooManyRequestsException(error.getString("message"));
		        			tmrex.setJsonMessage(error);
		        			throw tmrex;
		        		}	
	        		}
	        		errorMessage = error.getString("message");
	        	}
	        	throw new InferenceException("Model returned error message:\n\n" + errorMessage);
	        } finally {
	        	if (is != null) {
	        		try {is.close();} catch (Exception ex) {}
	        	}
	        }
		} catch (IOException ex) {
			throw new InferenceException("Communication to LLM failed", ex);	
		} catch (JSONException ex) {
			throw new InferenceException("Communication to LLM failed (couldn't parse response)", ex);
		}
	}
	
	
	public ResponseItem convertUserInputToResponseItem(String input) {
		return new OpenAITextResponse (input);
	}
	

	public class OpenAITextResponse extends ResponseItem implements TextResponse {

		String text;
		
		public OpenAITextResponse (String text) {
			super();
			this.text = text;
		}	
		
		public JSONObject toJSON() throws JSONException {
			JSONObject o = new JSONObject();
			o.put("content", getText());
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
	

	public class OpenAIToolCallRequest extends ToolCallRequest {

		public OpenAIToolCallRequest (String toolName) {
			super (null, toolName);
		}
		
		public JSONObject toJSON() throws JSONException {
			JSONObject obj = new JSONObject();
			obj.put("id", getToolRequestId());
			obj.put("type", "function");
			JSONObject function = new JSONObject (); { 
				obj.put("function", function);
				function.put ("name", getToolName());
				JSONObject args = new JSONObject();
				for (String key : getInputs().keySet()) {
					args.put(key, getInputs().get(key));
				}
				function.put("arguments", args.toString());
			}
			return obj;
		}
		
	}
	
	public ToolCallResponse createToolCallResponse (ToolCallRequest request) {
		OpenAIToolCallResponse r = new OpenAIToolCallResponse (request);
		return r;
	}

	public class OpenAIToolCallResponse extends ToolCallResponse {
		
		public OpenAIToolCallResponse (ToolCallRequest request) {
			super (request);
		}	
		
		public JSONObject toJSON() throws JSONException {
			return toJSON(false);
		}
		
		@Override
		public ToolCallResponse fromJSON(JSONObject toolCall) {
			// TODO Auto-generated method stub
			return null;
		}
		
		public JSONObject toJSON(boolean includeBinary) throws JSONException {
			JSONObject o = new JSONObject();
			o.put("tool_call_id", getToolCallRequest().getToolRequestId());
			if (getTextContent() != null) {
				o.put("content", getTextContent());
			} else if (getJsonResponse() != null) {
				o.put("content", getJsonResponse().toString());
			}
			return o;
		}
		 
		@Override
		public void setBinaryContent(BinaryContent binaryContent) {
			throw new IllegalArgumentException("Binary content now allowed in function call response for this model");
		}

	}
	
	class OpenAIToolCallRequestArray extends ToolCallRequestArray {
		
		public JSONObject toJSON() throws JSONException {
			JSONObject toolCalls = new JSONObject();
			if (getContent() != null) {
				toolCalls.put("content", getContent());
			}
			JSONArray toolCallsArray = new JSONArray();
			for (ToolCallRequest tcr : getToolCalls()) {
				JSONObject toolCallJson = tcr.toJSON();
				toolCallsArray.put(toolCallJson);
			}	
			toolCalls.put("tool_calls", toolCallsArray);			
			
			return toolCalls;
		}

	}
	
	ToolCallRequest parseToolCall (JSONObject response_part) throws JSONException {
		String id = response_part.getString("id");
		JSONObject function = response_part.getJSONObject("function");
		String name = function.getString("name");
		String arguments = function.getString("arguments");
		Tool tool = getTool(name);
		ToolCallRequest toolCall = new OpenAIToolCallRequest(name);
		toolCall.setToolRequestId(id);
		toolCall.setTool(tool);
		JSONObject argsObj = new JSONObject (arguments);
		if (tool != null) {
			List <Parameter> params = tool.getParameters();
			for (Parameter param : params) {
				String paramName = param.getKey();
				Object value = argsObj.get(paramName);
				// should do some validation here, perhaps.
				toolCall.setInputValue(paramName, value);
			}
		}		
		return toolCall;
	}	
	
	public ImageResponse createImageResponse(byte [] image, String contentType, String fileName, String contentText) {
		ImageResponse res = new OpenAIImageResponse();
		res.setBinaryData(image);
		res.setDataType(contentType);
		res.setMediaType("image");
		res.setAccompanyingInputText(contentText);
		return res;
	}
	
	class OpenAIImageResponse extends ImageResponse {
		public JSONObject toJSON() throws JSONException {
			JSONObject obj = new JSONObject();
			obj.put ("role", "user");
			JSONArray content = new JSONArray();
			if (getAccompanyingInputText() != null) {
				JSONObject textPart = new JSONObject();
				textPart.put("type","text");
				textPart.put("text", getAccompanyingInputText());
				content.put(textPart);
			}
			JSONObject imagePart = new JSONObject();
			imagePart.put("type", "image_url");
			JSONObject imageUrl = new JSONObject();
			imagePart.put("image_url", imageUrl);
			imageUrl.put("url", "data:"+this.getDataType()+";base64,"+toBase64());
			imageUrl.put("detail", "auto");

			content.put(imagePart);
			obj.put("content", content);
			return obj;
		}
	}
	
	@Override
	public void loadHistoryFrom(JSONObject history) {
		// TODO Auto-generated method stub
		
	}
	

}
