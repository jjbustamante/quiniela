package io.quiniela.api.team;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class TeamRankingIT extends AbstractIntegrationTest {

  @Autowired TeamRepository teams;

  @Test
  void seededTeamsHaveFifaRanking() {
    // Argentina (code ARG) is seeded with a ranking in V014.
    Team arg = teams.findByTournamentIdAndCode(1L, "ARG").orElseThrow();
    assertThat(arg.getFifaRanking()).isNotNull();
    assertThat(arg.getFifaRanking()).isBetween(1, 211);
  }

  @Test
  void playoffPlaceholdersHaveNullRanking() {
    Team k1 = teams.findByTournamentIdAndCode(1L, "TBD_K1").orElseThrow();
    assertThat(k1.getFifaRanking()).isNull();
  }
}
