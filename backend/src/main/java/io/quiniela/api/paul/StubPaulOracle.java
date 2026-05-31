package io.quiniela.api.paul;

/**
 * Used when no LLM provider is configured. Always fails so callers use their deterministic
 * fallback.
 */
public class StubPaulOracle implements PaulOracle {

  @Override
  public PaulPredictionResult predict(
      String systemPrompt, String userPrompt, String provider, String model) {
    throw new IllegalStateException("No LLM configured");
  }
}
