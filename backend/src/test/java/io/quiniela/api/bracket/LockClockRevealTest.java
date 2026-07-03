package io.quiniela.api.bracket;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bracket.LockClock.TournamentDeadlines;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LockClockRevealTest {

  private static final Instant PAST = Instant.parse("2020-01-01T00:00:00Z");
  private static final Instant FUTURE = Instant.parse("2999-01-01T00:00:00Z");
  private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");

  @Test
  void groupRevealableOnlyAfterGroupDeadline() {
    assertThat(LockClock.isGroupRevealable(NOW, new TournamentDeadlines(PAST, FUTURE))).isTrue();
    assertThat(LockClock.isGroupRevealable(NOW, new TournamentDeadlines(FUTURE, FUTURE))).isFalse();
  }

  @Test
  void nullGroupDeadlineIsNeverRevealable() {
    assertThat(LockClock.isGroupRevealable(NOW, new TournamentDeadlines(null, null))).isFalse();
  }
}
