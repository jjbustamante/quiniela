package io.quiniela.api.paul;

import java.util.Locale;

/**
 * Dispatches each prediction to the right provider oracle by the {@code provider} routing key, so a
 * single Paul run can span multiple LLM vendors. Gemini → {@link VertexPaulOracle} (genai SDK);
 * everything else (gpt-oss, Qwen, Llama, Mistral, DeepSeek) → {@link OpenAiCompatVertexOracle}
 * (Vertex OpenAI-compatible endpoint).
 */
public class RoutingPaulOracle implements PaulOracle {

  private final PaulOracle gemini;
  private final PaulOracle openAiCompat;

  public RoutingPaulOracle(PaulOracle gemini, PaulOracle openAiCompat) {
    this.gemini = gemini;
    this.openAiCompat = openAiCompat;
  }

  @Override
  public PaulPredictionResult predict(
      String systemPrompt, String userPrompt, String provider, String model) {
    String p = provider == null ? "" : provider.toLowerCase(Locale.ROOT);
    return switch (p) {
      case "vertex", "google", "gemini" ->
          gemini.predict(systemPrompt, userPrompt, provider, model);
      case "openai", "qwen", "maas", "llama", "meta", "mistral", "mistralai", "deepseek" ->
          openAiCompat.predict(systemPrompt, userPrompt, provider, model);
      default -> throw new IllegalArgumentException("Unknown Paul provider: " + provider);
    };
  }
}
