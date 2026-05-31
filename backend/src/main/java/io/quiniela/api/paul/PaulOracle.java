package io.quiniela.api.paul;

/** Narrow seam over the LLM so prediction logic is testable without Spring AI internals. */
public interface PaulOracle {

  /**
   * Ask the given model to produce a structured prediction.
   *
   * @throws RuntimeException if the model call fails or is unconfigured.
   */
  PaulPredictionResult predict(String systemPrompt, String userPrompt, String model);
}
