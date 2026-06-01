package io.quiniela.api.paul;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

/**
 * Vertex AI (Gemini) implementation of {@link PaulOracle}, built directly on Google's unified
 * {@code com.google.genai} SDK in Vertex mode (ADC auth via the Cloud Run runtime service account —
 * no API key; billed to GCP credits).
 *
 * <p>We use the raw SDK rather than Spring AI because Spring AI's Vertex AI Gemini module is not
 * published on the 2.0.x line this app's BOM requires. Serves the Gemini family only; the {@code
 * provider} arg is ignored ({@link RoutingPaulOracle} routes non-Gemini providers elsewhere).
 */
public class VertexPaulOracle implements PaulOracle {

  private final Client client;
  private final ObjectMapper mapper;

  public VertexPaulOracle(String projectId, String location) {
    this.client = Client.builder().vertexAI(true).project(projectId).location(location).build();
    // Construct our own Jackson 2 mapper rather than injecting one: Spring Boot 4 defaults to
    // Jackson 3 (tools.jackson), so no com.fasterxml ObjectMapper bean exists. Jackson 2 is on the
    // classpath transitively via google-genai.
    this.mapper = new ObjectMapper();
  }

  @Override
  public PaulPredictionResult predict(
      String systemPrompt, String userPrompt, String provider, String model) {
    String prompt =
        systemPrompt
            + "\n\n"
            + userPrompt
            + "\n\nResponde SOLO con un objeto JSON con exactamente estas claves: "
            + "{\"scoreT1\": entero >= 0, \"scoreT2\": entero >= 0, "
            + "\"confidence\": número entre 0 y 1, \"reasoning\": texto en español}.";
    // maxOutputTokens must cover the model's INTERNAL reasoning + the JSON answer.
    // gemini-3-* are thinking models: with the default (small) budget they spend it
    // all on reasoning, hit finishReason=MAX_TOKENS, and return empty/truncated text
    // → JSON parse fails → fallback. 8192 leaves ample room for both.
    GenerateContentConfig config =
        GenerateContentConfig.builder()
            .temperature(0.8f)
            .responseMimeType("application/json")
            .maxOutputTokens(8192)
            .build();
    try {
      GenerateContentResponse response = client.models.generateContent(model, prompt, config);
      String text = response.text();
      if (text == null || text.isBlank()) {
        throw new IllegalStateException("empty response text (likely truncated by token budget)");
      }
      return mapper.readValue(text, PaulPredictionResult.class);
    } catch (Exception e) {
      // Surface as unchecked so the prediction/ensemble services fall back to the
      // deterministic stub (a single failed call must not abort the whole batch).
      throw new IllegalStateException(
          "Vertex prediction failed for model " + model + ": " + e.getMessage(), e);
    }
  }
}
