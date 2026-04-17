package com.matt.llminterfaces;


@FunctionalInterface
public interface ToolRunner {

	void runTool (ToolCallRequest request);

}
