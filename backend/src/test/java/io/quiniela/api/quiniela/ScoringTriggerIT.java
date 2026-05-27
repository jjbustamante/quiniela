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

  @BeforeEach
  void resetMatchScores() {
    // Resets matches 1 (group-stage) and 73, 74 (knockout) before each test.
    new JdbcTemplate(dataSource)
        .update("UPDATE match SET score_t1 = NULL, score_t2 = NULL WHERE id IN (1, 73, 74)");
  }

  /** Create a user + quiniela + bet on match 1 (the first seeded group-stage match). */
  private Long setupBetOnMatch1(int betT1, int betT2) {
    var u = new User("g-sc-" + System.nanoTime(), "sc@example.com", "Sc", null, UserRole.PLAYER);
    u = users.save(u);
    var q = quinielas.save(new Quiniela(1L, u.getId()));
    bets.save(new Bet(q.getId(), 1L, betT1, betT2));
    return q.getId();
  }

  /** Create a user + quiniela + bet on a knockout match (round_id = 2, R32). */
  private Long setupBetOnKnockoutMatch(Long matchId, int betT1, int betT2) {
    var u =
        users.save(
            new User(
                "g-sck-" + System.nanoTime(), "sck@example.com", "Sck", null, UserRole.PLAYER));
    var q = quinielas.save(new Quiniela(1L, u.getId()));
    bets.save(new Bet(q.getId(), matchId, betT1, betT2));
    return q.getId();
  }

  private void setMatchResult(Long matchId, int t1, int t2) {
    new JdbcTemplate(dataSource)
        .update("UPDATE match SET score_t1 = ?, score_t2 = ? WHERE id = ?", t1, t2, matchId);
  }

  private int pointsOf(Long qId) {
    return quinielas.findById(qId).orElseThrow().getPoints();
  }

  // ── Group-stage scoring ──────────────────────────────────────────────────

  @Test
  void exactScoreAwardsSeven() {
    // bet 2-1, actual 2-1: outcome (3) + T1 (2) + T2 (2) + diff suppressed = 7.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 2, 1);
    assertThat(pointsOf(q)).isEqualTo(7);
  }

  @Test
  void winnerAndGoalDifferenceAwardsFour() {
    // bet 2-1, actual 3-2: outcome (3) + diff (1) = 4. No team-score match.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 3, 2);
    assertThat(pointsOf(q)).isEqualTo(4);
  }

  @Test
  void winnerWithOneTeamExactAwardsFive() {
    // bet 2-1, actual 2-0: outcome (3) + T1 exact (2) = 5.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 2, 0);
    assertThat(pointsOf(q)).isEqualTo(5);
  }

  @Test
  void winnerOnlyNoTeamScoreAwardsThree() {
    // bet 2-1, actual 5-0: outcome only (3). No T1, no T2, no diff.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 5, 0);
    assertThat(pointsOf(q)).isEqualTo(3);
  }

  @Test
  void correctDrawWithDifferentExactAwardsFour() {
    // bet 1-1, actual 0-0: outcome draw (3) + diff (1, both diffs are 0) = 4.
    var q = setupBetOnMatch1(1, 1);
    setMatchResult(1L, 0, 0);
    assertThat(pointsOf(q)).isEqualTo(4);
  }

  @Test
  void predictedDrawActualNotDrawWithOneTeamExactAwardsTwo() {
    // bet 1-1, actual 1-3: wrong outcome (draw vs team2-win), T1 exact (2) = 2.
    var q = setupBetOnMatch1(1, 1);
    setMatchResult(1L, 1, 3);
    assertThat(pointsOf(q)).isEqualTo(2);
  }

  @Test
  void wrongWinnerWithOneTeamExactAwardsTwo() {
    // bet 2-1, actual 0-1: wrong outcome (T1-win vs T2-win), T2 exact (2) = 2.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 0, 1);
    assertThat(pointsOf(q)).isEqualTo(2);
  }

  @Test
  void wrongWinnerNoPartialCreditAwardsZero() {
    // bet 2-1, actual 3-4: wrong outcome, no team-score match, wrong diff sign.
    var q = setupBetOnMatch1(2, 1);
    setMatchResult(1L, 3, 4);
    assertThat(pointsOf(q)).isEqualTo(0);
  }

  @Test
  void correctionUpdatesPointsDelta() {
    // Trigger's subtract-old-add-new math: same bet, scored against two different
    // actuals back to back. After the second UPDATE, points reflects only the new
    // contribution, not the sum of both.
    var q = setupBetOnMatch1(2, 1);

    // First: set match 2-1 (exact). bet 2-1 vs actual 2-1 = 7 pts.
    setMatchResult(1L, 2, 1);
    assertThat(pointsOf(q)).isEqualTo(7);

    // Correction: change to 2-0. bet 2-1 vs actual 2-0 = outcome (3) + T1 (2) = 5 pts.
    // Delta is -2 (7 -> 5), not 12 (7 + 5).
    setMatchResult(1L, 2, 0);
    assertThat(pointsOf(q)).isEqualTo(5);
  }

  // ── Knockout scoring (×2 on every component) ─────────────────────────────

  @Test
  void exactScoreKnockoutAwardsFourteen() {
    // bet 2-1, actual 2-1 on knockout: (3+2+2) * 2 = 14.
    var q = setupBetOnKnockoutMatch(73L, 2, 1);
    setMatchResult(73L, 2, 1);
    assertThat(pointsOf(q)).isEqualTo(14);
  }

  @Test
  void winnerAndGoalDifferenceKnockoutAwardsEight() {
    // bet 2-1, actual 3-2 on knockout: (3+1) * 2 = 8.
    var q = setupBetOnKnockoutMatch(73L, 2, 1);
    setMatchResult(73L, 3, 2);
    assertThat(pointsOf(q)).isEqualTo(8);
  }

  @Test
  void winnerWithOneTeamExactKnockoutAwardsTen() {
    // bet 2-1, actual 2-0 on knockout: (3+2) * 2 = 10.
    var q = setupBetOnKnockoutMatch(73L, 2, 1);
    setMatchResult(73L, 2, 0);
    assertThat(pointsOf(q)).isEqualTo(10);
  }

  @Test
  void wrongWinnerWithOneTeamExactKnockoutAwardsFour() {
    // bet 2-1, actual 0-1 on knockout: T2 only (2) * 2 = 4.
    var q = setupBetOnKnockoutMatch(74L, 2, 1);
    setMatchResult(74L, 0, 1);
    assertThat(pointsOf(q)).isEqualTo(4);
  }
}
