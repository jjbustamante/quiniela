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

class PaulServiceCachedIT extends AbstractIntegrationTest {

  @Autowired PaulService paul;
  @Autowired PaulPredictionRepository predictions;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired UserRepository users;

  @Test
  void suggestUsesCachedCandidateWhenPresent() {
    predictions.save(
        new PaulPrediction(
            1L,
            "google",
            "gemini-2.5-flash",
            PaulPrediction.KIND_CANDIDATE,
            3,
            0,
            new BigDecimal("0.80"),
            "Goleada cantada.",
            "es",
            PaulPrediction.SOURCE_AI));

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
            1L,
            "google",
            "gemini-2.5-flash",
            PaulPrediction.KIND_CANDIDATE,
            3,
            0,
            new BigDecimal("0.80"),
            "Goleada cantada.",
            "es",
            PaulPrediction.SOURCE_AI));

    paul.fillAllForUser(u.getId());

    Quiniela q = quinielas.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    Bet bet = bets.findByQuinielaIdAndMatchId(q.getId(), 1L).orElseThrow();
    assertThat(bet.getScoreT1()).isEqualTo(3);
    assertThat(bet.getScoreT2()).isZero();
  }
}
