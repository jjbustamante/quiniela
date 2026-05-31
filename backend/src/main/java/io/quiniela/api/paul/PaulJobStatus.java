package io.quiniela.api.paul;

import java.time.Instant;

/**
 * Snapshot of the current/last Paul batch job. One job runs at a time (see {@link PaulJobService}).
 * Returned by the admin generate/synthesize (202) and status endpoints so the caller can poll
 * progress instead of holding an HTTP request open across dozens of LLM calls.
 */
public record PaulJobStatus(
    String phase,
    String state,
    int processed,
    int total,
    String error,
    String startedAt,
    String finishedAt) {

  public static final String STATE_IDLE = "IDLE";
  public static final String STATE_RUNNING = "RUNNING";
  public static final String STATE_DONE = "DONE";
  public static final String STATE_FAILED = "FAILED";

  /** No job has ever run. */
  public static PaulJobStatus idle() {
    return new PaulJobStatus(null, STATE_IDLE, 0, 0, null, null, null);
  }

  public static PaulJobStatus running(String phase, Instant startedAt) {
    return new PaulJobStatus(phase, STATE_RUNNING, 0, 0, null, startedAt.toString(), null);
  }

  public PaulJobStatus withTotal(int total) {
    return new PaulJobStatus(phase, state, processed, total, error, startedAt, finishedAt);
  }

  public PaulJobStatus incProcessed() {
    return new PaulJobStatus(phase, state, processed + 1, total, error, startedAt, finishedAt);
  }

  public PaulJobStatus done(Instant finishedAt) {
    return new PaulJobStatus(
        phase, STATE_DONE, processed, total, null, startedAt, finishedAt.toString());
  }

  public PaulJobStatus failed(String error, Instant finishedAt) {
    return new PaulJobStatus(
        phase, STATE_FAILED, processed, total, error, startedAt, finishedAt.toString());
  }
}
