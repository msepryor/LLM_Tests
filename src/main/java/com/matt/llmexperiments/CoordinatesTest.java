package com.matt.llmexperiments;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;

import org.json.JSONException;
import org.json.JSONObject;

import com.matt.llminterfaces.APIKey;
import com.matt.llminterfaces.APIKey.Model;
import com.matt.llminterfaces.Agent;
import com.matt.llminterfaces.AgentConfig;
import com.matt.llminterfaces.InferenceException;
import com.matt.llminterfaces.LLMModel;
import com.matt.llminterfaces.NoKeyAvailableException;
import com.matt.llminterfaces.ResponseItem;
import com.matt.llminterfaces.TextResponse;

/**
 * Experimental agent that evaluates how accurately a multimodal language model
 * can estimate the coordinates of a white circle rendered within a synthetic image.
 * <p>
 * The test generates black images containing a single white circle at a random
 * location, submits each image to the configured model, and records the model's
 * predicted coordinates alongside the true coordinates and error deltas.
 * 
 * @author Matt Pryor / Student S24007537
 */
public class CoordinatesTest extends Agent {

	/** Maximum number of tokens permitted for model inference. */
	public static final int MAX_TOKENS = 4000;

	/** Sampling temperature used for model inference. */
	public static float TEMPERATURE = 0.4F;

	/** Loaded agent configuration for this test class. */
	static AgentConfig config = loadConfig();

	/**
	 * Loads the agent configuration associated with this class.
	 *
	 * @return the loaded {@link AgentConfig}
	 */
	static AgentConfig loadConfig() {
		return AgentConfig.getConfig(CoordinatesTest.class);
	}

	/**
	 * Constructs a new {@code CoordinatesTest}.
	 */
	public CoordinatesTest() {
		// TODO Auto-generated constructor stub
	}

	/**
	 * Generates a synthetic test image containing a white circle on a black background.
	 *
	 * @param width the width of the generated image in pixels
	 * @param height the height of the generated image in pixels
	 * @param x the x-coordinate of the circle centre
	 * @param y the y-coordinate of the circle centre
	 * @param circleSize the diameter of the circle in pixels
	 * @return the generated image
	 */
	Image generateTestImage(int width, int height, int x, int y, int circleSize) {
		BufferedImage bi = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = (Graphics2D) bi.getGraphics();
		g.setColor(Color.black);
		g.fillRect(0, 0, width, height);
		g.setColor(Color.white);
		g.fillOval(x - (int) circleSize / 2, y - (int) circleSize / 2, circleSize, circleSize);
		return bi;
	}

	/**
	 * Executes a batch of coordinate-estimation tests against the specified model.
	 * <p>
	 * For each test case, a random circle position is generated, the image is sent
	 * to the model, and the predicted coordinates are written to a results file
	 * together with the true coordinates and prediction error.
	 *
	 * @param modelType the model provider/type to use
	 * @param modelVariant the specific model variant identifier
	 * @param numTests the number of random test images to generate and evaluate
	 * @param width the width of each test image in pixels
	 * @param height the height of each test image in pixels
	 * @param circleSize the diameter of the white circle in pixels
	 * @throws IOException if an error occurs writing the results file
	 * @throws NoKeyAvailableException if no API key is available for the selected model
	 */
	void runTest(Model modelType, String modelVariant, int numTests, int width, int height, int circleSize)
			throws IOException, NoKeyAvailableException {
		System.out.println("Running test: " + modelType + "(" + modelVariant + ") - image size " + width + "x" + height);

		LLMModel model = LLMModel.loadUserModel(
				modelType,
				APIKey.getInternalAPIKey(modelType),
				modelVariant);
		String sysPrompt = config.getSystemPrompt();
		sysPrompt = sysPrompt.replace("{width}", "" + width);
		sysPrompt = sysPrompt.replace("{height}", "" + height);
		model.addSystemPrompt(sysPrompt);
		Path results = Path.of("./res/results/" + model.getModelName() + "-" + numTests + "x" + width + "x" + height + "-"
				+ (new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date())) + ".txt");
		for (int x = 0; x < numTests; x++) {
			int x1 = Math.min(Math.max((int) Math.round(Math.random() * width), circleSize), width - circleSize);
			int y1 = Math.min(Math.max((int) Math.round(Math.random() * height), circleSize), height - circleSize);
			Image i = generateTestImage(width, height, x1, y1, circleSize);
			try {
				int[] estimate = callLLM(i, model.createClone());
				try (FileWriter fw = new FileWriter(results.toFile(), true)) {
					int delta_x = x1 - estimate[0];
					int delta_y = y1 - estimate[1];
					System.out.println((x + 1) + " (" + delta_x + "," + delta_y + ")");
					fw.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date()) + "," + x1 + "," + y1 + ","
							+ estimate[0] + "," + estimate[1] + "," + delta_x + "," + delta_y + "\n");
				}
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	/**
	 * Sends an image to the language model and attempts to extract predicted
	 * coordinates from the model response.
	 * <p>
	 * The model is expected to return JSON containing integer fields
	 * {@code x-position} and {@code y-position}.
	 *
	 * @param i the image to analyse
	 * @param model the model instance to call
	 * @return an integer array containing the predicted coordinates in the form
	 *         {@code [x, y]}
	 * @throws NoKeyAvailableException if no API key is available
	 * @throws IOException if image serialisation fails
	 * @throws InferenceException if the model response is invalid or does not contain
	 *         the required coordinate data
	 */
	int[] callLLM(Image i, LLMModel model) throws NoKeyAvailableException, IOException, InferenceException {

		List<ResponseItem> l = new ArrayList<ResponseItem>();
		l.add(model.convertUserInputToResponseItem("Please estimate the coordinates of the white circle"));
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		ImageIO.write((BufferedImage) i, "png", baos);
		byte[] imageBytes = baos.toByteArray();
		l.add(model.createImageResponse(imageBytes, "image/png", "file1.png", null));
		List<ResponseItem> responses = model.getModelResponse(l);
		int[] coords = null;
		for (ResponseItem r : responses) {
			if (r instanceof TextResponse) {
				try {
					JSONObject obj = extractJSON((TextResponse) r);
					int xpos = obj.getInt("x-position");
					int ypos = obj.getInt("y-position");
					coords = new int[] { xpos, ypos };
				} catch (JSONException ex) {
					throw new InferenceException("Invalid JSON response: " + ((TextResponse) r).getText());
				}
			}
		}
		if (coords != null) {
			return coords;
		} else {
			throw new InferenceException("Model did not respond correctly - check logs");
		}

	}

	/**
	 * Displays the supplied image in a simple Swing window.
	 *
	 * @param i the image to display
	 */
	void displayImage(Image i) {
		JLabel l = new JLabel();
		l.setIcon(new ImageIcon(i));
		JFrame f = new JFrame();
		f.getContentPane().add(l);
		f.pack();
		f.setLocationRelativeTo(null);
		f.setVisible(true);
	}

	/**
	 * Entry point for running the coordinate estimation experiment.
	 * <p>
	 * Configures one or more models and invokes {@link #runTest(Model, String, int, int, int, int)}
	 * for each.
	 *
	 * @param args command-line arguments (currently unused)
	 * @throws Exception if an unrecoverable error occurs during execution
	 */
	public static void main(String[] args) throws Exception {

		Model [] models = {Model.CLAUDE, Model.XAI, Model.MISTRAL, Model.OPENAI, Model.GEMINI};
		String [] variants = {"claude-sonnet-4-6","grok-4.20-0309-non-reasoning","mistral-large-2512","gpt-5.4","gemini-3.1-pro-preview"};
		int [] sizes = {64, 72, 96, 128};
		for (int i = 0; i < models.length; i++) {
			for (int s : sizes) {
				new CoordinatesTest().runTest(models[i], variants[i], 15, s, s, 6);
			}
		}
	}
}