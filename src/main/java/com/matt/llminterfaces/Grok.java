package com.matt.llminterfaces;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import com.matt.llminterfaces.APIKey.Model;

/**
 * Grok-specific implementation of {@link LLMModel}.
 *
 * Translates the framework's internal request/response abstractions into the
 * JSON schema expected by the XAI Messages API, including support for:
 * system prompts, tool definitions, tool calls, tool results, text responses,
 * image inputs, and persisted conversation history.
 * 
 * @author Matt Pryor
 */

public class Grok extends Claude {
	
	public Grok () {
		super (Model.XAI);
	}
	
	
	protected void setHeaders (HttpURLConnection conn) {
		conn.setRequestProperty ("Authorization", "Bearer "+getApiKey());
		conn.setRequestProperty ("content-type", "application/json");
	}
}
