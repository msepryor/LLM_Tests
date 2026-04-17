package com.matt.llminterfaces;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Vector;
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
 * Claude-specific implementation of {@link LLMModel}.
 *
 * Translates the framework's internal request/response abstractions into the
 * JSON schema expected by the Claude Messages API, including support for:
 * system prompts, tool definitions, tool calls, tool results, text responses,
 * image inputs, and persisted conversation history.
 * 
 * @author Matt Pryor
 */


public class Claude extends LLMModel {
	
	public Claude () {
		this (Model.CLAUDE);
	}
	
	public Claude (Model model) {
		super(model);
	}
	
	public void loadPromptsEtc (Document configDoc) throws IOException {
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
	
	protected JSONArray availableToolsToJSON () throws JSONException {
		if (getAvailableTools().size() > 0) {
			JSONArray array = new JSONArray();
			for (Tool tool : getAvailableTools()) {
				JSONObject toolObj = new JSONObject();
				toolObj.put("name", tool.getToolName());
				toolObj.put("description", tool.getToolDescription());
				JSONObject schema = new JSONObject();
				schema.put("type", "object");
				JSONObject properties = new JSONObject();
				for (Tool.Parameter p : tool.getParameters()) {
					JSONObject param = new JSONObject();
					param.put("type", p.getType().getTypeString());
					param.put("description", p.getDescription());
					if (!p.getAcceptableValues().isEmpty()) {
						JSONArray en = new JSONArray();
						for (Object o : p.getAcceptableValues()) {
							en.put(o);
						}
						param.put("enum", en);
					}	
					properties.put(p.getKey(), param);
					schema.put("properties", properties);
				}
				toolObj.put("input_schema", schema);
				Tool.Parameter [] required = tool.getParameters().stream().filter(Tool.Parameter::isRequired).toArray(Tool.Parameter[]::new);
				if (required.length > 0) {
					JSONArray reqArray = new JSONArray();
					for (Tool.Parameter rp : required) {
						reqArray.put(rp.getKey());
					}
					schema.put("required", reqArray);
				}
				array.put(toolObj);
			}
			
			return array;
		}	
		return null;
	}

	protected String getJson (List <ResponseItem> userRequest, int maxTokens, float temperature) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("model", getModelName());
		json.put("max_tokens", maxTokens);
		//.put("temperature", temperature); // they deprecated this!
		JSONArray systemPrompts = new JSONArray();
		for (SystemPrompt p : getPromptItems()) {
			JSONObject promptObj = new JSONObject();
			promptObj.put("type","text");
			promptObj.put("text", p.getPromptText().trim());
			if (p.isCache()) {
				JSONObject cache = new JSONObject();
				cache.put("type", "ephemeral");
				promptObj.put("cache_control", cache);
			}
			systemPrompts.put(promptObj);
		}
		json.put("system", systemPrompts);

		JSONArray tools = availableToolsToJSON();
		if (tools != null) {
			json.put("tools", tools);
		}
		
		JSONArray messages = new JSONArray();
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
			user.put("content", content);

			JSONObject assistant = new JSONObject();
			assistant.put("role","assistant");
			content = new JSONArray();
			for (ResponseItem i : systemMessages) {
				content.put(i.toJSON());
			}
			assistant.put("content", content);
			
			messages.put(user);
			messages.put(assistant);
		}
		JSONObject newUserR = new JSONObject();
		newUserR.put("role", "user");
		JSONArray content = new JSONArray();
		for (ResponseItem i : userRequest) {
			if (i instanceof ImageResponse) {
				ImageResponse ir = (ImageResponse) i;
				JSONObject img = ir.toJSON();
				content.put(img);
				if (ir.getAccompanyingInputText() != null) {
					JSONObject text = new JSONObject();
					text.put("type","text");
					text.put("text",((ImageResponse)i).getAccompanyingInputText());
					content.put(text);
				}
			} else {
				content.put(i.toJSON());
			}
		}
		newUserR.put("content", content);
		messages.put(newUserR);
		json.put("messages", messages);
		
		return json.toString(5);
	}
	
	@Override
	public ResponseItem convertUserInputToResponseItem(String input) {
		return new ClaudeTextResponse (input);
	}
	
	protected void setHeaders (HttpURLConnection conn) {
		conn.setRequestProperty ("x-api-key", getApiKey());
		conn.setRequestProperty ("anthropic-version", getApiVersion());
		conn.setRequestProperty ("content-type", "application/json");
	}
	
	protected List <ResponseItem> infer(List <ResponseItem> userRequest, int maxTokens, float temperature) throws InferenceException {
		try {

			URL u = URI.create(getEndPoint()).toURL();
			HttpURLConnection c = (HttpURLConnection) u.openConnection();
			c.setRequestMethod("POST");
			setHeaders(c);
			
			String data = getJson(userRequest, maxTokens, temperature);
			logRequest(data);
			
			int l = data.length();
			c.setRequestProperty("Content-Length" , ""+l);
			c.setDoOutput(true);
			
			OutputStreamWriter wr = new OutputStreamWriter(c.getOutputStream(), StandardCharsets.UTF_8);
	        wr.write(data.toString());
	        wr.flush();
	        InputStream is = null;
	        try {
	        	is = c.getInputStream();
	        	InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
	            BufferedReader reader = new BufferedReader(isr);
                
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
	        	String json = response.toString();
	        	JSONObject o = new JSONObject(json);
				logResponse(o.toString(5));
	        	String stopReason = o.getString("stop_reason");
				JSONArray messages = o.getJSONArray("content");
				ArrayList <ResponseItem> responses = new ArrayList <ResponseItem> ();
				for (int x=0; x < messages.length(); x++) {
					JSONObject response_part = messages.getJSONObject(x);
					String type = response_part.getString("type");
					if ("tool_use".equals(type)) {
						ClaudeToolCallRequest toolCall = parseToolCall(response_part);
						responses.add(toolCall);
					} else if ("text".equals(type)) {
						String text = response_part.getString("text");
						ClaudeTextResponse textresponse = new ClaudeTextResponse (text);
						textresponse.setText(text);
						responses.add(textresponse);
					}
					
				}
				if ("max_tokens".equals(stopReason)) {
					throw new ResponseTruncatedException();
				}

				return responses;
	        } catch (IOException ex) {
				if (is != null) {
					try {
						is.close();
					} catch (Exception ex2) {}
				}
	        	is = c.getErrorStream();
	        	if (is != null) {
		        	ByteArrayOutputStream baos = new ByteArrayOutputStream();
		        	byte [] buff = new byte [1024];
		        	int n = -1;
		        	while ((n = is.read(buff)) != -1) {
		        		baos.write (buff, 0, n);
		        	}
		        	String json = new String (baos.toByteArray(), StandardCharsets.UTF_8);
		        	InferenceException infEx = new InferenceException("Model returned error message:\n\n"+json);
		        	try {
		        		JSONObject o = new JSONObject(json);
		        		if (o.has("error")) {
		        			o = o.getJSONObject("error");
		        			String type = o.getString("type");
							if (type.equals("rate_limit_error")) {
								TooManyRequestsException tmrex = new TooManyRequestsException(type);
								tmrex.setJsonMessage(o);
								throw tmrex;
							}
		        		}
		        		infEx.setJsonMessage(o);
		        	} catch (JSONException jex) {
		        	}
		        	try {
		        		logErrorResponse(new JSONObject(json).toString(5));
		        	} catch (JSONException ex2) {
		        		logErrorResponse(json);
		        	}
	
		        	throw infEx;
	        	}
	        	throw new InferenceException (ex);
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
	
	ClaudeToolCallRequest parseToolCall (JSONObject response_part) throws JSONException {
		String id = response_part.getString("id");
		String name = response_part.getString("name");
		JSONObject input = response_part.getJSONObject("input");
		Tool tool = getTool(name);
		ClaudeToolCallRequest toolCall = new ClaudeToolCallRequest(id, name);
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
	
	public static void main (String [] args) throws Exception {
//		Claude c = new Claude("./res/llm_sysprompts/infer_function/claude-config.xml");
//		Tool t = new Tool ();
//		t.setToolName("getTemperature");
//		t.setToolDescription("Gets the current temperature for the specified city");
//		Parameter p = new Parameter();
//		p.setKey("city");
//		p.setType(DataType.STRING);
//		p.setRequired(true);
//		t.addParameter(p);
//		c.addTool(t);
//		t = new Tool();
//		t.setToolName("getWindSpeed");
//		t.setToolDescription("Gets the current wind speed for the specified city");
//		p = new Parameter();
//		p.setKey("city");
//		p.setType(DataType.STRING);
//		p.setRequired(true);
//		t.addParameter(p);
//		c.addTool(t);
//		List <ResponseItem> l = c.getModelResponse ("What's the temperature and wind speed in London today Claude? Remember that there was a thermonuclear attack last week, so the weather is a bit crazy.", 1024, 0.5F);
//		List <ResponseItem> responses = new ArrayList <ResponseItem> ();

		
	}	
	
	public class ClaudeTextResponse extends ResponseItem implements TextResponse {

		String text;
		
		public ClaudeTextResponse (String text) {
			super();
			this.text = text;
		}	
		
		public JSONObject toJSON() throws JSONException {
			JSONObject o = new JSONObject();
			o.put("type","text");
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
	

	public class ClaudeToolCallRequest extends ToolCallRequest {

		public ClaudeToolCallRequest (String id, String toolName) {
			super (id, toolName);
		}
		
		public JSONObject toJSON() throws JSONException {
			JSONObject obj = new JSONObject();
			obj.put("type", "tool_use");
			obj.put("id", getToolRequestId());
			obj.put("name", getToolName());
			JSONObject input = new JSONObject();
			for (String key : getInputs().keySet()) {
				input.put(key, getInputs().get(key));
			}
			obj.put("input", input);
			return obj;
		}
		
	}
	
	public ToolCallResponse createToolCallResponse (ToolCallRequest request) {
		ClaudeToolCallResponse r = new ClaudeToolCallResponse (request.getToolRequestId());
		return r;
	}

	public class ClaudeToolCallResponse extends ToolCallResponse {
	
		String id;
		
		public ClaudeToolCallResponse (String id) {
			super ();
			setId(id);
		}	
		
		public JSONObject toJSON() throws JSONException {
			return toJSON(true);
		}
		
		@Override
		public ToolCallResponse fromJSON(JSONObject toolCallJson) {
			ToolCallResponse r = new ClaudeToolCallResponse(toolCallJson.getString("tool_use_id"));
			JSONArray contentArr = toolCallJson.getJSONArray("content");
			for (int x=0; x < contentArr.length(); x++) {
				JSONObject part = contentArr.getJSONObject(x);
				String type = part.getString("type");
				if (type.equals("text")) {
					r.setTextContent(part.getString("text"));
				} else if (type.equals("base64")) {
					BinaryContent bin = new BinaryContent();
					JSONObject source = part.getJSONObject("source");
					String data = source.getString("data");
					bin.setBinaryData(Base64.getDecoder().decode(data));
					r.setBinaryContent(new BinaryContent());
					// todo finish this for base 64, if you want to get your drawing tool working.
				}
			}
			return r;			
		}
		
		public JSONObject toJSON(boolean includeBinary) throws JSONException {
			JSONObject o = new JSONObject();
			o.put("type", "tool_result");
			o.put("tool_use_id", getId());
			JSONArray content = new JSONArray();
			if (getTextContent() != null) {
				ClaudeTextResponse t = new ClaudeTextResponse(getTextContent());
				content.put(t.toJSON());
			}	
			if (getBinaryContent() != null && includeBinary) {
				BinaryContent b = getBinaryContent();
				JSONObject bin = new JSONObject();
				bin.put ("type", b.getDataType());
				JSONObject source = new JSONObject();
				source.put ("type", "base64");
				source.put ("media_type", b.getMediaType());
				
				byte [] bytes = Base64.getEncoder().encode(b.getBinaryData());
				String str = new String (bytes, StandardCharsets.UTF_8);
				source.put ("data", str);
				bin.put("source", source);
				content.put(bin);
			}	
			o.put("content", content);
			return o;
		}

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

	}

	
	public ImageResponse createImageResponseX(byte [] image, String contentType, String fileName, String contentText) {
		ImageResponse res = new ClaudeImageResponse();
		res.setBinaryData(image);
		res.setDataType(contentType);
		res.setMediaType("image");
		res.setAccompanyingInputText(contentText);
		return res;
	}
	
	@Override
	public ImageResponse createImageResponse(byte[] image, String contentType, String fileName, String contentText) {
		return createImageResponseX (image, contentType, fileName, contentText);
	}
	
	class ClaudeImageResponse extends ImageResponse {
		public JSONObject toJSON() throws JSONException {
			JSONObject obj = new JSONObject();
			obj.put("type","image");
			JSONObject imagePart = new JSONObject();
			imagePart.put("type", "base64");
			imagePart.put("media_type", getDataType());
			imagePart.put("data", toBase64());
			obj.put("source", imagePart);
			return obj;
		}
	}
	
	@Override
	public void loadHistoryFrom(JSONObject history) {
		Vector <MessagePair> messagePairs = new Vector <MessagePair> ();
		setMessagePairs(messagePairs);
		if (history.has("messages")) {
			JSONArray messagesArray = history.getJSONArray("messages");
			int messageCount = messagesArray.length();
			for (int x=0; x < messagesArray.length(); x+=2) {
				List <ResponseItem> userRequest = new ArrayList <ResponseItem> ();
				List <ResponseItem> systemResponse = new ArrayList <ResponseItem> ();
				
				MessagePair mp = new MessagePair(userRequest, systemResponse);
				JSONObject messageObject = messagesArray.getJSONObject(x);
				String role = messageObject.getString("role");
				if ("user".equals(role)) {
					JSONArray messages = messageObject.getJSONArray("content");
					for (int y=0; y < messages.length(); y++) {
						JSONObject response_part = messages.getJSONObject(y);
						String type = response_part.getString("type");
						if ("tool_result".equals(type)) {
							ToolCallResponse tcr = new ClaudeToolCallResponse(null).fromJSON(response_part);
							userRequest.add(tcr);
						} else if ("text".equals(type)) {
							String text = response_part.getString("text");
							ClaudeTextResponse tr = new ClaudeTextResponse(text);
							userRequest.add(tr);
						}
					}
				} else {
					throw new UncheckedIOException("messages appear to be in the wrong order ("+role+" found where user expected)", null);
				}
				int responseCount = x+1;
				if (messagesArray.length() >= responseCount+1) {
					messageObject = messagesArray.getJSONObject(responseCount);
					role = messageObject.getString("role");
					if ("assistant".equals(role)) {
						JSONArray messages = messageObject.getJSONArray("content");
						for (int y=0; y < messages.length(); y++) {
							JSONObject response_part = messages.getJSONObject(y);
							String type = response_part.getString("type");
							if ("tool_use".equals(type)) {
								ClaudeToolCallRequest toolCall = parseToolCall(response_part);
								systemResponse.add(toolCall);
							} else if ("text".equals(type)) {
								String text = response_part.getString("text");
								ClaudeTextResponse textresponse = new ClaudeTextResponse (text);
								textresponse.setText(text);
								systemResponse.add(textresponse);
							}
							
						}
					} else {
						throw new UncheckedIOException("messages appear to be in the wrong order ("+role+" found where assistant expected)", null);
					}
					messagePairs.add(mp);
				}
			}
		}
	}
		
}
