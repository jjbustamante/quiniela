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

class PaulRevealIT extends AbstractIntegrationTest {

  @Autowired PaulService paul;
  @Autowired PaulPredictionRepository predictions;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired UserRepository users;

  private void seedOfficial(long matchId, int s1, int s2) {
    predictions.save(
        new PaulPrediction(
            matchId,
            "google",
            "ensemble",
            PaulPrediction.KIND_OFFICIAL,
            s1,
            s2,
            new BigDecimal("0.75"),
            "Oficial.",
            "es",
            PaulPrediction.SOURCE_AI));
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
}
