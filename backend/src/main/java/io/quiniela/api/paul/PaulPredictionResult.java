package io.quiniela.api.paul;

/** Structured output returned by the LLM. Scores are non-negative; confidence in [0,1]. */
public record PaulPredictionResult(int scoreT1, int scoreT2, double confidence, String reasoning) {}
