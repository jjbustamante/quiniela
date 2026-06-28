package io.quiniela.api.match;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.team.TeamRepository;
import java.time.Instant;
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

  @Test
  void openMatchesIncludeFutureGroupAndExcludeTeamlessKnockout() {
    var open =
        matches
            .findByTournamentIdAndTeam1IdIsNotNullAndTeam2IdIsNotNullAndKickoffAtAfterOrderByKickoffAtAsc(
                1L, Instant.now());
    // 72 group matches are reanchored into the future (V021) and have teams.
    // Knockout seed matches have NULL teams, so they are excluded.
    assertThat(open).hasSize(72);
    assertThat(open).allMatch(m -> m.getTeam1Id() != null && m.getTeam2Id() != null);
  }
}
