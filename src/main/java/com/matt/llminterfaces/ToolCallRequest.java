package com.matt.llminterfaces;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class ToolCallRequest extends ResponseItem {
	String toolRequestId;
	String toolName;
	Tool tool;
	
	Map <String, Object> inputs = new HashMap<String, Object> ();
	
	public ToolCallRequest (String id, String toolName) {
		this.toolRequestId = id;
		this.toolName = toolName;
	}
	
	public void setInputValue (String input, Object value) {
		this.inputs.put(input, value);
	}
	
	public Object getInputValue (String inputKey) {
		return inputs.get(inputKey);
	}	
	
	public Object[] getInputValueAsArray(String inputKey) {
	    if (inputs.containsKey(inputKey)) {
	        Object o = inputs.get(inputKey);

	        if (o instanceof JSONArray) {
	            JSONArray arr = (JSONArray) o;
	            List <String> list = new ArrayList <String> ();
	            for (int i = 0; i < arr.length(); i++) {
	                list.add(String.valueOf(arr.get(i)));
	            }
	            return list.toArray(new String[0]);
	        }

	        if (o instanceof String) {
	            String str = ((String) o).trim();

	            if (str.startsWith("[") && str.endsWith("]")) {
	                str = str.substring(1, str.length() - 1); // strip brackets
	                String[] parts = str.split(",");
	                for (int i = 0; i < parts.length; i++) {
	                    parts[i] = parts[i].trim();
	                    if ((parts[i].startsWith("'") && parts[i].endsWith("'")) ||
	                        (parts[i].startsWith("\"") && parts[i].endsWith("\""))) {
	                        parts[i] = parts[i].substring(1, parts[i].length() - 1);
	                    }
	                }
	                return parts;
	            }
	        }

	        return new String[]{String.valueOf(o)};
	    }

	    return null;
	}


	public String getToolRequestId() {
		return toolRequestId;
	}

	public void setToolRequestId(String toolRequestId) {
		this.toolRequestId = toolRequestId;
	}

	public String getToolName() {
		return toolName;
	}

	public void setToolName(String toolName) {
		this.toolName = toolName;
	}

	public Tool getTool() {
		return tool;
	}

	public void setTool(Tool tool) {
		this.tool = tool;
	}
	
	
	public abstract JSONObject toJSON() throws JSONException;

	public Map<String, Object> getInputs() {
		return inputs;
	}
	
	@Override
	public String toString() {
		String str = getToolName();
		for (String k : getInputs().keySet()) {
			str+=k+"="+getInputs().get(k)+" ";
		}
		return str;
	}

}
