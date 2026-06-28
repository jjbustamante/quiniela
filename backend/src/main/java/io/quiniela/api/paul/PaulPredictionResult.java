package io.quiniela.api.paul;

/**
 * Structured output returned by the LLM. Scores are non-negative; confidence in [0,1]. {@code
 * advancing} is the team that progresses on a knockout regulation draw: {@code "LOCAL"} (team 1),
 * {@code "VISITANTE"} (team 2), or {@code null} (group / decisive).
 */
public record PaulPredictionResult(
    int scoreT1, int scoreT2, double confidence, String reasoning, String advancing) {}
