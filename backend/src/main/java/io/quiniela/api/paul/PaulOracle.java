package io.quiniela.api.paul;

/** Narrow seam over the LLM so prediction logic is testable without provider internals. */
public interface PaulOracle {

  /**
   * Ask the given provider's model to produce a structured prediction.
   *
   * @param provider routing key (e.g. {@code vertex}, {@code openai}); impls that serve a single
   *     provider may ignore it.
   * @param model the model id (e.g. {@code gemini-2.5-pro}, {@code openai/gpt-oss-120b-maas}).
   * @throws RuntimeException if the model call fails or is unconfigured.
   */
  PaulPredictionResult predict(
      String systemPrompt, String userPrompt, String provider, String model);
}
