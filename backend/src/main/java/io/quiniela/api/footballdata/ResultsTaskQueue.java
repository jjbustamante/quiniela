package io.quiniela.api.footballdata;

import java.time.Instant;

/**
 * Enqueues a deferred per-match result check. Implementations: Cloud Tasks (prod), no-op (local).
 */
public interface ResultsTaskQueue {

  /**
   * Schedule a POST to /internal/sync/results?matchId={matchId} at {@code when}.
   *
   * @param dedupName stable task name; Cloud Tasks dedups by name so re-planning is idempotent.
   */
  void enqueue(long matchId, Instant when, String dedupName);
}
