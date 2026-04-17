package com.matt.llminterfaces;

import java.util.HashMap;
import java.util.Map;

public class APIKey {
	
	String keyName;
	String keyValue;
	int keyId;
	
	Model model;
	
	public static enum Model {
		CLAUDE, MISTRAL, OPENAI, GEMINI, XAI
	};
	
	static Map <Model, String> cachedKeys = new HashMap <> ();
	
	/** These are keys used for presence internal functions like auto-doc and task generation.*/

	public static String getInternalAPIKey(Model purpose) throws NoKeyAvailableException {
		synchronized (cachedKeys) {
			if (cachedKeys.containsKey(purpose)) {
				return cachedKeys.get(purpose);
			}
			String key = retrieveInternalAPIKey(purpose);
			cachedKeys.put(purpose, key);
			return key;
		}	
	}
	
	private static String retrieveInternalAPIKey (Model purpose) throws NoKeyAvailableException {
		String env_key = purpose.name()+"_API_KEY";
		if (System.getenv(env_key) != null) {
			return System.getenv(env_key);
		} else if (System.getProperty(env_key) != null) {
			return System.getProperty(env_key);
		}
		throw new NoKeyAvailableException ("No API Key could be obtained for the operation.");
	}
}
