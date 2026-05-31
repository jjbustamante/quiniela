package io.quiniela.api.paul;

/**
 * Used when no Gemini API key is configured. Always fails so callers use their deterministic
 * fallback.
 */
public class StubPaulOracle implements PaulOracle {

  @Override
  public PaulPredictionResult predict(String systemPrompt, String userPrompt, String model) {
    throw new IllegalStateException("No LLM configured (GEMINI_API_KEY unset)");
  }
}
