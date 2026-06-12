package io.quiniela.api.footballdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Test double: records enqueue calls instead of hitting Cloud Tasks. */
public class FakeResultsTaskQueue implements ResultsTaskQueue {

  public record Enqueued(long matchId, Instant when, String dedupName) {}

  public final List<Enqueued> calls = new ArrayList<>();

  @Override
  public void enqueue(long matchId, Instant when, String dedupName) {
    calls.add(new Enqueued(matchId, when, dedupName));
  }
}
