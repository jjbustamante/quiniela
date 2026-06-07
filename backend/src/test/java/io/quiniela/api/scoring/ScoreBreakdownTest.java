package io.quiniela.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ScoreBreakdownTest {

  @Test
  void exactGroupScore() {
    var b = ScoreBreakdown.of(false, 2, 1, 2, 1, null, null, 1);
    assertThat(b.outcome()).isEqualTo(3);
    assertThat(b.team1Exact()).isEqualTo(2);
    assertThat(b.team2Exact()).isEqualTo(2);
    assertThat(b.goalDiff()).isZero();
    assertThat(b.multiplier()).isEqualTo(1);
    assertThat(b.total()).isEqualTo(7);
  }

  @Test
  void winnerAndGoalDifference() {
    var b = ScoreBreakdown.of(false, 2, 1, 3, 2, null, null, 1);
    assertThat(b.outcome()).isEqualTo(3);
    assertThat(b.team1Exact()).isZero();
    assertThat(b.team2Exact()).isZero();
    assertThat(b.goalDiff()).isEqualTo(1);
    assertThat(b.total()).isEqualTo(4);
  }

  @Test
  void knockoutMultiplierScalesTotal() {
    assertThat(ScoreBreakdown.of(true, 2, 1, 2, 1, null, null, 3).total()).isEqualTo(21);
  }

  @Test
  void knockoutDrawAwardsOutcomeWhenAdvancingTeamMatches() {
    var b = ScoreBreakdown.of(true, 1, 1, 0, 0, 5L, 5L, 2);
    assertThat(b.outcome()).isEqualTo(3);
    assertThat(b.goalDiff()).isEqualTo(1);
    assertThat(b.total()).isEqualTo(8);
  }

  @Test
  void knockoutDrawZeroOutcomeWhenAdvancingTeamWrong() {
    var b = ScoreBreakdown.of(true, 1, 1, 0, 0, 5L, 7L, 2);
    assertThat(b.outcome()).isZero();
    assertThat(b.goalDiff()).isEqualTo(1);
    assertThat(b.total()).isEqualTo(2);
  }

  @Test
  void unplayedMatchIsAllZero() {
    var b = ScoreBreakdown.of(false, 2, 1, null, null, null, null, 1);
    assertThat(b.outcome()).isZero();
    assertThat(b.team1Exact()).isZero();
    assertThat(b.team2Exact()).isZero();
    assertThat(b.goalDiff()).isZero();
    assertThat(b.total()).isZero();
  }
}
