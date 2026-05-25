package io.quiniela.api.quiniela;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.bet.Bet;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class QuinielaRepositoryIT extends AbstractIntegrationTest {

  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired UserRepository users;

  @Test
  void canPersistQuinielaAndBet() {
    var u = new User("g-q1", "qtest@example.com", "QTest", null, UserRole.CAPTAIN);
    u.setInvitePath("qtest-abc");
    u = users.save(u);

    var q = new Quiniela(1L, u.getId());
    q = quinielas.save(q);
    assertThat(q.getId()).isNotNull();
    assertThat(q.getPoints()).isEqualTo(0);

    // match 1 is the first seeded group-stage match.
    var b = new Bet(q.getId(), 1L, 2, 1);
    bets.save(b);

    var fetched = quinielas.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    assertThat(fetched.getId()).isEqualTo(q.getId());

    var matchBet = bets.findByQuinielaIdAndMatchId(q.getId(), 1L).orElseThrow();
    assertThat(matchBet.getScoreT1()).isEqualTo(2);
    assertThat(matchBet.getScoreT2()).isEqualTo(1);
  }
}
