package com.matt.llminterfaces;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public interface LLMModelSelection {
	
	public static List <LLMModel> loadLLMConfigs (File configFolder) {
		File [] files = configFolder.listFiles();
		List <LLMModel> models = new ArrayList <LLMModel> ();
		for (File f : files) {
			if (f.getName().endsWith("config.xml")) {
				try {
					LLMModel model = LLMModel.loadFromConfig (f);
					models.add(model);
				} catch (IOException ex) {
					ex.printStackTrace();
				}
			}
		}
		return models;
	}
	
	public static LLMModel getModel (String descr, List <LLMModel> availableModels) {
		for (LLMModel m : availableModels) {
			if (m.toString().equals(descr)) {
				return m;
			}	
		}
		return availableModels.get(0);
		
	}
	
}
