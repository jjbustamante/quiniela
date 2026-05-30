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
    assertThat(LockClock.isMatchRevealable(NOW, new TournamentDeadlines(PAST, FUTURE), "GROUP"))
        .isTrue();
    assertThat(LockClock.isMatchRevealable(NOW, new TournamentDeadlines(FUTURE, FUTURE), "GROUP"))
        .isFalse();
  }

  @Test
  void knockoutRevealableOnlyAfterKnockoutDeadline() {
    assertThat(LockClock.isMatchRevealable(NOW, new TournamentDeadlines(PAST, PAST), "R32"))
        .isTrue();
    assertThat(LockClock.isMatchRevealable(NOW, new TournamentDeadlines(PAST, FUTURE), "R32"))
        .isFalse();
  }

  @Test
  void nullDeadlineIsNeverRevealable() {
    assertThat(LockClock.isMatchRevealable(NOW, new TournamentDeadlines(null, null), "GROUP"))
        .isFalse();
    assertThat(LockClock.isMatchRevealable(NOW, new TournamentDeadlines(null, null), "FINAL"))
        .isFalse();
  }
}
