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
		switch (purpose) {
		case CLAUDE:
			return System.getenv("CLAUDE_API_KEY");
		case MISTRAL:
			return System.getenv("MISTRAL_API_KEY");
		case OPENAI:
			return System.getenv("OPENAI_API_KEY");
		case GEMINI:
			return System.getenv("GEMINI_API_KEY");
		case XAI:
			return System.getenv("XAI_API_KEY");
		default:
			throw new NoKeyAvailableException ("No API Key could be obtained for the operation. Please consult Presence support:\n\nsupport@presencebpm.com");
		}
	}
	


}
