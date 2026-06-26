package io.quiniela.api.footballdata;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Test double: records enqueue calls instead of hitting Cloud Tasks. */
public class FakeResultsTaskQueue implements ResultsTaskQueue {

  public record Enqueued(long matchId, Instant when, String dedupName) {}

  public record FixturesRefresh(Instant when, String dedupName) {}

  public final List<Enqueued> calls = new ArrayList<>();

  public final List<FixturesRefresh> fixturesCalls = new ArrayList<>();

  @Override
  public void enqueue(long matchId, Instant when, String dedupName) {
    calls.add(new Enqueued(matchId, when, dedupName));
  }

  @Override
  public void enqueueFixturesRefresh(Instant when, String dedupName) {
    fixturesCalls.add(new FixturesRefresh(when, dedupName));
  }
}
