package io.quiniela.api.paul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

/**
 * Vertex AI implementation of {@link PaulOracle}, built directly on Google's unified {@code
 * com.google.genai} SDK in Vertex mode (ADC auth via the Cloud Run runtime service account — no API
 * key; billed to GCP credits).
 *
 * <p>We use the raw SDK rather than Spring AI because Spring AI's Vertex AI Gemini module is not
 * published on the 2.0.x line this app's BOM requires. The {@link PaulOracle} seam keeps this a
 * pure implementation detail: if/when Spring AI ships Vertex for 2.x, swap this for a
 * ChatClient-based impl with no change to callers.
 */
public class VertexPaulOracle implements PaulOracle {

  private final Client client;
  private final ObjectMapper mapper;

  public VertexPaulOracle(String projectId, String location, ObjectMapper mapper) {
    this.client = Client.builder().vertexAI(true).project(projectId).location(location).build();
    this.mapper = mapper;
  }

  @Override
  public PaulPredictionResult predict(String systemPrompt, String userPrompt, String model) {
    String prompt =
        systemPrompt
            + "\n\n"
            + userPrompt
            + "\n\nResponde SOLO con un objeto JSON con exactamente estas claves: "
            + "{\"scoreT1\": entero >= 0, \"scoreT2\": entero >= 0, "
            + "\"confidence\": número entre 0 y 1, \"reasoning\": texto en español}.";
    GenerateContentConfig config =
        GenerateContentConfig.builder()
            .temperature(0.8f)
            .responseMimeType("application/json")
            .build();
    try {
      GenerateContentResponse response = client.models.generateContent(model, prompt, config);
      return mapper.readValue(response.text(), PaulPredictionResult.class);
    } catch (Exception e) {
      // Surface as unchecked so the prediction/ensemble services fall back to the
      // deterministic stub (a single failed call must not abort the whole batch).
      throw new IllegalStateException(
          "Vertex prediction failed for model " + model + ": " + e.getMessage(), e);
    }
  }
}
