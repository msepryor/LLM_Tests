package com.matt.llminterfaces;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
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
 * Mistral-specific implementation of {@link LLMModel}.
 *
 * Translates the framework's internal request/response abstractions into the
 * JSON schema expected by the Mistral Messages API, including support for:
 * system prompts, tool definitions, tool calls, tool results, text responses,
 * image inputs, and persisted conversation history.
 * 
 * @author Matt Pryor
 */

public class Mistral extends LLMModel {
	
	boolean safePrompt = false;

	public Mistral() {
		super(Model.MISTRAL);
		
	}

	public void loadPromptsEtc (Document configDoc) throws IOException {
		Document doc = configDoc;
		Element root = (Element) doc.getElementsByTagName("llm-config").item(0);
		
		setModelName(root.getAttribute("modelname"));
		setEndPoint(root.getAttribute("endpoint"));
		setSafePrompt(Boolean.parseBoolean(root.getAttribute("safeprompt")));

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

	protected List <ResponseItem> infer(List <ResponseItem> userRequest, int maxTokens, float temperature) throws InferenceException {
		try {
			JSONArray messages = new JSONArray();
			for (Iterator <SystemPrompt> i = getPromptItems().iterator(); i.hasNext();) {
				JSONObject sysPObj = new JSONObject();
				SystemPrompt sp = i.next();
				String prompt = sp.getPromptText().trim();
				
				sysPObj.put("role", "system");
				sysPObj.put("content", prompt);
				messages.put(sysPObj);
			}

			for (MessagePair mp : getMessagePairs()) {
				List <ResponseItem> userMessages = (List <ResponseItem>) mp.getUserMessage();
				List <ResponseItem> systemMessages = (List <ResponseItem>) mp.getSystemMessage();
				
				for (ResponseItem i : userMessages) {
					JSONObject json = i.toJSON();
					json.put("role", i instanceof ToolCallResponse ? "tool" : "user");
					messages.put(json);
				}

				JSONArray toolCallsArray = new JSONArray();
				JSONObject sysmessage = new JSONObject();
				sysmessage.put("role", "assistant");
				for (ResponseItem i : systemMessages) {
					if (i instanceof TextResponse) {
						sysmessage.put("content", ((TextResponse) i).getText());
					} else if (i instanceof ToolCallRequest) {
						JSONObject toolCallObj = ((ToolCallRequest) i).toJSON();
						toolCallsArray.put(toolCallObj);
					}
				}
				if (toolCallsArray.length() > 0) {
					sysmessage.put("tool_calls", toolCallsArray);
				}
				messages.put(sysmessage);
			}
			
			for (ResponseItem i : userRequest) {
				JSONObject o = i.toJSON();
				if (!(i instanceof ToolCallResponse)) {
					o.put("role", "user");
					if (i instanceof ImageResponse) {
						JSONObject content = new JSONObject();
					} else {
						messages.put(o);						
					}
				}

			}

			JSONObject dataObj = new JSONObject();
			dataObj.put("model", getModelName());
			dataObj.put("max_tokens", maxTokens);
			dataObj.put("n",1);
			dataObj.put("safe_prompt", String.valueOf(isSafePrompt()));
			dataObj.put("messages", messages);
			if (!getAvailableTools().isEmpty()) {
				dataObj.put("tools", availableToolsToJSON());
			}	
			
			String data = dataObj.toString(5);

			logRequest(data);
			
			int l = data.length();
			URL u = new URL(getEndPoint());
			HttpURLConnection c = (HttpURLConnection) u.openConnection();
			c.setRequestMethod("POST");
			c.setRequestProperty ("Authorization", "Bearer "+getApiKey());
			c.setRequestProperty ("Content-Type", "application/json");
			c.setRequestProperty ("Accept", "application/json");
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
	        	String responseContent = null;

				List <ResponseItem> list = new ArrayList <ResponseItem> ();
	        	
				if (msg.has("content")) {
					responseContent = msg.getString("content");
					list.add(new MistralTextResponse(responseContent));
				}

				if (msg.has("tool_calls")) {
					Object obj = msg.get("tool_calls");
					if (obj instanceof JSONArray) {
						JSONArray toolCalls = (JSONArray) obj;
						for (int x=0; x < toolCalls.length(); x++) {
							JSONObject toolCall = (JSONObject) toolCalls.get(x);
							MistralToolCallRequest toolCallReq = parseToolCall(toolCall);
							list.add(toolCallReq);
						}	
					}
				}
				
				if ("length".equals(stopReason)) {
					throw new ResponseTruncatedException(responseContent);
				}

				return list;
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
	        	JSONObject jsonObj = new JSONObject (json);
				logErrorResponse(jsonObj.toString(5));

	        	throw new InferenceException("Model returned error message:\n\n"+json);
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

	protected boolean isSafePrompt() {
		return safePrompt;
	}

	protected void setSafePrompt(boolean safePrompt) {
		this.safePrompt = safePrompt;
	}
	
	
	public ResponseItem convertUserInputToResponseItem(String input) {
		return new MistralTextResponse (input);
	}
	

	public class MistralTextResponse extends ResponseItem implements TextResponse {

		String text;
		
		public MistralTextResponse (String text) {
			super();
			this.text = text;
		}	
		
		public JSONObject toJSON() throws JSONException {
			JSONObject o = new JSONObject();
			o.put ("content", getText());
			return o;
		}

		public String getText() {
			return this.text;
		}

		public void setText(String text) {
			this.text = text;
		}
		
	}	
	

	public class MistralToolCallRequest extends ToolCallRequest {

		public MistralToolCallRequest (String toolRequestId, String toolName) {
			super (toolRequestId, toolName);
		}
		
		public JSONObject toJSON() throws JSONException {
			JSONObject obj = new JSONObject();
			obj.put("type", "function");
			obj.put("id", getToolRequestId());
			JSONObject function = new JSONObject ();
			obj.put("function", function);
				function.put("name", getToolName());
				
				JSONObject arguments = new JSONObject();
					for (String key : getInputs().keySet()) {
						arguments.put(key, getInputs().get(key));
					}
				String argsAsString = arguments.toString();
				function.put("arguments", argsAsString);
			return obj;
		}
	}
	
	public ToolCallResponse createToolCallResponse (ToolCallRequest request) {
		ToolCallResponse r = new MistralToolCallResponse (request);
		return r;
	}

	public class MistralToolCallResponse extends ToolCallResponse {
		
		ToolCallRequest request;
		
		public MistralToolCallResponse (ToolCallRequest request) {
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
			o.put("tool_call_id", getRequest().getToolRequestId());
			o.put("role", "tool");
			o.put("content", getTextContent());
			return o;
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
			this.request = request;
		}

	}
	
	
	protected JSONArray availableToolsToJSON () throws JSONException {
		if (getAvailableTools().size() > 0) {
			JSONArray array = new JSONArray();
			for (Tool tool : getAvailableTools()) {
				JSONObject toolObj = new JSONObject();
				toolObj.put("type", "function");
				JSONObject function = new JSONObject();
				toolObj.put("function", function);
				function.put("name", tool.getToolName());
				function.put("description", tool.getToolDescription());
				JSONObject parameters = new JSONObject();
				parameters.put("type", "object");
				function.put("parameters", parameters);
				JSONObject properties = new JSONObject();
				for (Tool.Parameter p : tool.getParameters()) {
					JSONObject property = new JSONObject();
					property.put("type", p.getType().getTypeString());
					property.put("description", p.getDescription());
					properties.put(p.getKey(), property);
				}
				parameters.put("properties", properties);
				Tool.Parameter [] required = tool.getParameters().stream().filter(Tool.Parameter::isRequired).toArray(Tool.Parameter[]::new);
				if (required.length > 0) {
					JSONArray reqArray = new JSONArray();
					for (Tool.Parameter rp : required) {
						reqArray.put(rp.getKey());
					}
					parameters.put("required", reqArray);
				}
				array.put(toolObj);
			}
			
			return array;
		}	
		return null;
	}
	
	MistralToolCallRequest parseToolCall (JSONObject response_part) throws JSONException {
		String id = response_part.getString("id");
		JSONObject function = response_part.getJSONObject("function");
		String name = function.getString("name");
		String argsStr = function.getString ("arguments");
		JSONObject args = new JSONObject (argsStr); 
		Tool tool = getTool(name);
		MistralToolCallRequest toolCall = new MistralToolCallRequest (id, name);
		toolCall.setTool(tool);
		if (tool != null) {
			List <Parameter> params = tool.getParameters();
			for (Parameter param : params) {
				String paramName = param.getKey();
				Object value = args.get(paramName);
				toolCall.setInputValue(paramName, value);
			}
		}		
		return toolCall;
	}	

	@Override
	public ImageResponse createImageResponse(byte[] image, String contentType, String fileName, String contentText) {
		return new MistralImageResponse(contentType, image);
	}
	
	@Override
	public void loadHistoryFrom(JSONObject history) {
		// TODO Auto-generated method stub
		
	}
	
	class MistralImageResponse extends ImageResponse {

		String contentType;
		byte [] bytes;
		
		private MistralImageResponse (String contentType, byte [] bytes) {
			this.contentType = contentType;
			this.bytes = bytes;
		}
		
		public JSONObject toJSON() throws JSONException {
            //"type": "image_url",
            //"image_url": f"data:image/jpeg;base64,{base64_image}"
            JSONObject imgJSON = new JSONObject();
            imgJSON.put("type", "image_url");
            imgJSON.put("image_url", "data:"+contentType+";base64,"+Base64.getEncoder().encodeToString(bytes));
            JSONObject o = new JSONObject ();
            o.put("content", imgJSON);
            return o;
		}
	}
	
}
