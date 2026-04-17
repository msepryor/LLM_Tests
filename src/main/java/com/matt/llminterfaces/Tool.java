package com.matt.llminterfaces;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.json.*;

import com.matt.llminterfaces.Tool.Parameter.DataType;


public class Tool {
	
	private Runnable runnable;
	private String id;
	private String toolName;
	private String toolDescription;
	private List <Parameter> parameters = new ArrayList<Tool.Parameter> ();
	private ToolRunner runner;
	
	public Tool (Path definition) throws JSONException, IOException {
		this();
		String str = Files.readString(definition);
		JSONObject json = new JSONObject(str);
		String jsonStr = json.toString(5);
		setToolName(json.getString("name"));
		String description = (json.getString("description"));

		StringBuilder descBuilder = new StringBuilder();
		descBuilder.append(description);
		if (json.has("usage_guidelines")) {
			JSONObject usage = json.getJSONObject("usage_guidelines");
			if (usage.has("when_to_use")) {
				JSONArray arr = usage.getJSONArray("when_to_use");
				StringBuilder sb = new StringBuilder();
				for (int i=0; i < arr.length(); i++) {
					sb.append(arr.getString(i)+"\n");
				}
				descBuilder.append("## When to use\n");
				descBuilder.append(sb.toString());
				
			}
			if (json.has("performance_considerations")) {
				JSONArray arr = usage.getJSONArray("performance_considerations");
				StringBuilder sb = new StringBuilder();
				for (int i=0; i < arr.length(); i++) {
					sb.append(arr.getString(i)+"\n");
				}
				descBuilder.append("## Performance Considerations\n");
				descBuilder.append(sb.toString());
			}
			if (json.has("best_practices")) {
				JSONArray arr = usage.getJSONArray("best_practices");
				StringBuilder sb = new StringBuilder();
				for (int i=0; i < arr.length(); i++) {
					sb.append(arr.getString(i)+"\n");
				}
				descBuilder.append("## Best Practices\n");
				descBuilder.append(sb.toString());
			}
		}
		setToolDescription(descBuilder.toString());
		JSONArray parameters = json.getJSONArray("parameters");
		for (int x=0; x < parameters.length(); x++) {
			JSONObject param = parameters.getJSONObject(x);
			String name = param.getString ("name");
 			String paramDesc = null;
			if (param.get("description") instanceof String) {
				paramDesc = param.getString("description");
			} else if (param.get("description") instanceof JSONArray) {
				JSONArray descArr = param.getJSONArray("description");
				StringBuilder sb = new StringBuilder();
				for (int d=0; d < descArr.length(); d++) {
					sb.append(descArr.get(d)+"\n");
				}
				paramDesc = sb.toString();
			}
			String type = param.getString("type");
			boolean required = param.getBoolean("required");
			Parameter parameter = new Parameter();
			if (param.has("enum")) {
				JSONArray enumJSON = param.getJSONArray("enum");
				ArrayList <Object> values = new ArrayList <Object> ();
				for (int e=0; e < enumJSON.length(); e++) {
					String v = enumJSON.getString(e);
					values.add(v);
				}
				parameter.setAcceptableValues(values);
			}			

			parameter.setKey(name);
			parameter.setDescription(paramDesc);
			parameter.setRequired(required);
			parameter.setType(DataType.valueOf(type.toUpperCase()));
			addParameter(parameter);
		}
	}
	
	public Tool () {
		
	}
	
	public static class Parameter {
		public static enum DataType {
			
			STRING ("string"),INTEGER("integer"),NUMBER("number"),OBJECT("object"),ARRAY("array"),BOOLEAN("boolean"),NULL("null");
			
			String typeString;
			private DataType (String typeString) {
				this.typeString = typeString;
			}
			public String getTypeString() {
				return typeString;
			}
			public void setTypeString(String typeString) {
				this.typeString = typeString;
			}
		}	
		String key;
		DataType type;
		boolean required;
		String description;
		List <Object> acceptableValues = new ArrayList <Object> ();
		
		public String getKey() {
			return key;
		}
		public void setKey(String key) {
			this.key = key;
		}
		public boolean isRequired() {
			return required;
		}
		public void setRequired(boolean required) {
			this.required = required;
		}
		public String getDescription() {
			return description;
		}
		public void setDescription(String description) {
			this.description = description;
		}
		public DataType getType() {
			return type;
		}
		public void setType(DataType type) {
			this.type = type;
		}
		public List<Object> getAcceptableValues() {
			return acceptableValues;
		}
		public void setAcceptableValues(List<Object> acceptableValues) {
			this.acceptableValues = acceptableValues;
		}
	}

	public String getToolName() {
		return toolName;
	}

	public void setToolName(String toolName) {
		this.toolName = toolName;
	}

	public String getToolDescription() {
		return toolDescription;
	}

	public void setToolDescription(String toolDescription) {
		this.toolDescription = toolDescription;
	}

	public List<Parameter> getParameters() {
		return parameters;
	}

	public void setParameters(List<Parameter> parameters) {
		this.parameters = parameters;
	}
	
	public void addParameter (Parameter param) {
		this.parameters.add(param);
	}
	
	public void addRequiredParameter (Parameter param) {
		param.setRequired(true);
		this.parameters.add(param);
	}

	public ToolRunner getRunner() {
		return runner;
	}

	public void setRunner(ToolRunner runner) {
		this.runner = runner;
	}

}
