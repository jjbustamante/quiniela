package io.quiniela.api.match;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.team.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MatchRepositoryIT extends AbstractIntegrationTest {

  @Autowired MatchRepository matches;
  @Autowired RoundRepository rounds;
  @Autowired TeamRepository teams;

  @Test
  void groupAHasSixMatches() {
    var groupAMatches = matches.findByTournamentIdAndGroupCodeOrderByKickoffAtAsc(1L, "A");
    assertThat(groupAMatches).hasSize(6);
    assertThat(groupAMatches).allMatch(m -> "A".equals(m.getGroupCode()));
  }

  @Test
  void groupRoundExists() {
    var round = rounds.findByTournamentIdAndCode(1L, "GROUP").orElseThrow();
    assertThat(round.getName()).isEqualTo("Fase de grupos");
    assertThat(round.getSequence()).isEqualTo(1);
  }

  @Test
  void teamHasFlagEmoji() {
    var spain = teams.findByTournamentIdAndCode(1L, "ESP").orElseThrow();
    assertThat(spain.getName()).isEqualTo("España");
    assertThat(spain.getFlagEmoji()).isEqualTo("🇪🇸");
    assertThat(spain.getGroupCode()).isEqualTo("F");
  }
}
