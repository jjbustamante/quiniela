package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.UserRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PaulRevealIT extends AbstractIntegrationTest {

  @Autowired PaulService paul;
  @Autowired PaulPredictionRepository predictions;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired UserRepository users;
  @Autowired JdbcTemplate jdbc;

  private void seedOfficial(long matchId, int s1, int s2) {
    predictions.save(
        new PaulPrediction(
            "paul",
            matchId,
            "google",
            "ensemble",
            PaulPrediction.KIND_OFFICIAL,
            s1,
            s2,
            new BigDecimal("0.75"),
            "Oficial.",
            "es",
            PaulPrediction.SOURCE_AI,
            null));
  }

  @Test
  void revealCreatesPaulQuinielaAndSnapshotsOfficialBets() {
    seedOfficial(1L, 2, 1);
    seedOfficial(2L, 0, 0);

    var result = paul.reveal();
    assertThat(result.betsCreated()).isEqualTo(2);

    var paulUser = users.findByGoogleSub("paul-bot-oracle").orElseThrow();
    Quiniela pq = quinielas.findByPoolIdAndUserId(1L, paulUser.getId()).orElseThrow();
    assertThat(bets.findByQuinielaId(pq.getId())).hasSize(2);
    var bet1 = bets.findByQuinielaIdAndMatchId(pq.getId(), 1L).orElseThrow();
    assertThat(bet1.getScoreT1()).isEqualTo(2);
    assertThat(bet1.getScoreT2()).isEqualTo(1);
  }

  @Test
  void revealIsIdempotent() {
    seedOfficial(1L, 2, 1);
    paul.reveal();
    var second = paul.reveal();
    assertThat(second.betsCreated()).isZero(); // already snapshotted

    var paulUser = users.findByGoogleSub("paul-bot-oracle").orElseThrow();
    Quiniela pq = quinielas.findByPoolIdAndUserId(1L, paulUser.getId()).orElseThrow();
    assertThat(bets.findByQuinielaId(pq.getId())).hasSize(1);
  }

  @Test
  void revealSnapshotsKnockoutOfficialWinner() {
    long koMatch = 9041L;
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        koMatch);
    predictions.save(
        new PaulPrediction(
            "paul",
            koMatch,
            "google",
            "ensemble",
            PaulPrediction.KIND_OFFICIAL,
            1,
            1,
            null,
            "empate, avanza local",
            "es",
            PaulPrediction.SOURCE_AI,
            1L));
    try {
      paul.reveal();
      Long pwid =
          jdbc.queryForObject(
              "SELECT b.predicted_winner_id FROM bet b"
                  + " JOIN quiniela q ON q.id = b.quiniela_id"
                  + " JOIN users u ON u.id = q.user_id"
                  + " WHERE u.google_sub = 'paul-bot-oracle' AND b.match_id = ?",
              Long.class,
              koMatch);
      assertThat(pwid).isEqualTo(1L);
    } finally {
      jdbc.update(
          "DELETE FROM bet WHERE quiniela_id IN"
              + " (SELECT q.id FROM quiniela q JOIN users u ON u.id = q.user_id"
              + " WHERE u.google_sub = 'paul-bot-oracle')");
      jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", koMatch);
      jdbc.update("DELETE FROM match WHERE id = ?", koMatch);
    }
  }
}
