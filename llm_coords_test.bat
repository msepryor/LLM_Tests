@echo off

REM ============================================
REM LLMTestTool Runner
REM ============================================
REM Ensure:
REM 1. Java (JRE 23+) is installed and on PATH
REM 2. API keys are set below (or via environment variables)
REM ============================================

REM --- Set API keys (REQUIRED) ---
REM Replace with your actual keys or set externally

set CLAUDE_API_KEY=your_key_here
set MISTRAL_API_KEY=your_key_here
set OPENAI_API_KEY=your_key_here
set GEMINI_API_KEY=your_key_here
set XAI_API_KEY=your_key_here

REM --- Run the application ---
java -jar target\LLMTestTool-0.0.1-SNAPSHOT.jar

pause