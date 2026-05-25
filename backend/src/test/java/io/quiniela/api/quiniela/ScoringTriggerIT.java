package io.quiniela.api.quiniela;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.match.MatchRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ScoringTriggerIT extends AbstractIntegrationTest {

  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired MatchRepository matches;
  @Autowired UserRepository users;
  @Autowired DataSource dataSource;

  /**
   * Reset match 1 scores to NULL before each test so that each test starts from a clean state.
   * Parent's @BeforeEach (cleanWritableTables) runs first deleting bets/quinielas, then this runs.
   * With no bets referencing match 1, the trigger loop is empty and safe.
   */
  @BeforeEach
  void resetMatchOne() {
    new JdbcTemplate(dataSource)
        .update("UPDATE match SET score_t1 = NULL, score_t2 = NULL WHERE id = 1");
  }

  /** Helper: create a user + quiniela + bet on match 1 (the first seeded group-stage match). */
  private Long setupBetOnMatch1(int betT1, int betT2) {
    var u = new User("g-sc-" + System.nanoTime(), "sc@example.com", "Sc", null, UserRole.PLAYER);
    u = users.save(u);
    var q = quinielas.save(new Quiniela(1L, u.getId()));
    bets.save(new Bet(q.getId(), 1L, betT1, betT2));
    return q.getId();
  }

  private void setMatchResult(Long matchId, int t1, int t2) {
    new JdbcTemplate(dataSource)
        .update("UPDATE match SET score_t1 = ?, score_t2 = ? WHERE id = ?", t1, t2, matchId);
  }

  private int pointsOf(Long qId) {
    return quinielas.findById(qId).orElseThrow().getPoints();
  }

  @Test
  void exactScoreAwardsFivePoints() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 2, 1);
    assertThat(pointsOf(q)).isEqualTo(5);
  }

  @Test
  void correctWinnerAndGoalDifferenceAwardsThree() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 3, 2);
    assertThat(pointsOf(q)).isEqualTo(3);
  }

  @Test
  void correctWinnerOnlyAwardsTwo() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 4, 0);
    assertThat(pointsOf(q)).isEqualTo(2);
  }

  @Test
  void correctDrawAwardsTwo() {
    var q = setupBetOnMatch1(1, 1);
    setMatchResult(1L, 0, 0);
    assertThat(pointsOf(q)).isEqualTo(2);
  }

  @Test
  void wrongWinnerAwardsZero() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 0, 3);
    assertThat(pointsOf(q)).isEqualTo(0);
  }

  @Test
  void resultCorrectionUpdatesPointsDelta() {
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 2, 1); // exact — +5
    assertThat(pointsOf(q)).isEqualTo(5);
    setMatchResult(1L, 3, 2); // winner + diff — should become +3 (delta -2)
    assertThat(pointsOf(q)).isEqualTo(3);
    setMatchResult(1L, 0, 3); // miss — delta -3
    assertThat(pointsOf(q)).isEqualTo(0);
  }
}
