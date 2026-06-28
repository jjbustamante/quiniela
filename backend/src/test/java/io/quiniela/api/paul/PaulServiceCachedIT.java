package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PaulServiceCachedIT extends AbstractIntegrationTest {

  @Autowired PaulService paul;
  @Autowired PaulPredictionRepository predictions;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  @Test
  void suggestUsesCachedCandidateWhenPresent() {
    predictions.save(
        new PaulPrediction(
            "paul",
            1L,
            "google",
            "gemini-2.5-flash",
            PaulPrediction.KIND_CANDIDATE,
            3,
            0,
            new BigDecimal("0.80"),
            "Goleada cantada.",
            "es",
            PaulPrediction.SOURCE_AI,
            null));

    var s = paul.suggestForMatch(1L);
    assertThat(s.scoreT1()).isEqualTo(3);
    assertThat(s.scoreT2()).isZero();
    assertThat(s.reasoning()).isEqualTo("Goleada cantada.");
  }

  @Test
  void fillCopiesCachedCandidateForThatMatch() {
    var u = users.save(new User("g-cache", "c@example.com", "C", null, UserRole.PLAYER));
    predictions.save(
        new PaulPrediction(
            "paul",
            1L,
            "google",
            "gemini-2.5-flash",
            PaulPrediction.KIND_CANDIDATE,
            3,
            0,
            new BigDecimal("0.80"),
            "Goleada cantada.",
            "es",
            PaulPrediction.SOURCE_AI,
            null));

    paul.fillAllForUser(u.getId());

    Quiniela q = quinielas.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    Bet bet = bets.findByQuinielaIdAndMatchId(q.getId(), 1L).orElseThrow();
    assertThat(bet.getScoreT1()).isEqualTo(3);
    assertThat(bet.getScoreT2()).isZero();
  }

  @org.junit.jupiter.api.Test
  void fillAllIncludesOpenKnockoutRoundWithPredictedWinner() {
    long koMatch = 9031L;
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        koMatch);
    predictions.save(
        new PaulPrediction(
            "paul",
            koMatch,
            "google",
            "gemini-2.5-pro",
            PaulPrediction.KIND_CANDIDATE,
            1,
            1,
            null,
            "empate, avanza local",
            "es",
            PaulPrediction.SOURCE_AI,
            1L));
    Long userId =
        jdbc.queryForObject(
            "INSERT INTO users (google_sub, email, display_name, role) "
                + "VALUES ('filler-user', 'filler@test', 'Filler', 'player') RETURNING id",
            Long.class);
    try {
      paul.fillAllForUser(userId);
      Long pwid =
          jdbc.queryForObject(
              "SELECT b.predicted_winner_id FROM bet b JOIN quiniela q ON q.id = b.quiniela_id"
                  + " WHERE q.user_id = ? AND b.match_id = ?",
              Long.class,
              userId,
              koMatch);
      assertThat(pwid).isEqualTo(1L);
    } finally {
      jdbc.update(
          "DELETE FROM bet WHERE quiniela_id IN (SELECT id FROM quiniela WHERE user_id = ?)",
          userId);
      jdbc.update("DELETE FROM quiniela WHERE user_id = ?", userId);
      jdbc.update("DELETE FROM users WHERE id = ?", userId);
      jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", koMatch);
      jdbc.update("DELETE FROM match WHERE id = ?", koMatch);
    }
  }

  @org.junit.jupiter.api.Test
  void suggestForKnockoutMatchReturnsPredictedWinner() {
    long koMatch = 9021L;
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        koMatch);
    predictions.save(
        new PaulPrediction(
            "paul",
            koMatch,
            "google",
            "gemini-2.5-pro",
            PaulPrediction.KIND_CANDIDATE,
            1,
            1,
            null,
            "empate, avanza local",
            "es",
            PaulPrediction.SOURCE_AI,
            1L));
    try {
      PaulService.Suggestion s = paul.suggestForMatch(koMatch);
      assertThat(s.scoreT1()).isEqualTo(1);
      assertThat(s.predictedWinnerId()).isEqualTo(1L);
    } finally {
      jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", koMatch);
      jdbc.update("DELETE FROM match WHERE id = ?", koMatch);
    }
  }
}
