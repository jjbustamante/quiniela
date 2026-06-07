package io.quiniela.api.match;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class RoundMultiplierMappingIT extends AbstractIntegrationTest {

  @Autowired RoundRepository rounds;

  @Test
  void readsSeededMultipliers() {
    int group = rounds.findByTournamentIdAndCode(1L, "GROUP").orElseThrow().getPointsMultiplier();
    int r32 = rounds.findByTournamentIdAndCode(1L, "R32").orElseThrow().getPointsMultiplier();
    assertThat(group).isEqualTo(1);
    assertThat(r32).isEqualTo(2);
  }
}
