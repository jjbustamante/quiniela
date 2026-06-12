package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.footballdata.FootballDataClient.CompetitionMatchesResponse;
import io.quiniela.api.footballdata.FootballDataClient.MatchApi;
import io.quiniela.api.footballdata.FootballDataClient.MatchScore;
import io.quiniela.api.footballdata.FootballDataClient.MatchScoreFull;
import io.quiniela.api.footballdata.FootballDataClient.MatchTeam;
import io.quiniela.api.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FootballDataSyncFreezeIT extends AbstractIntegrationTest {

  @Autowired FootballDataSyncService sync;
  @Autowired JdbcTemplate jdbc;

  @Test
  void doesNotRewriteAnAlreadyPlayedMatch() {
    // A GROUP match (round_id resolved from seeded rounds) already played 2-1.
    Long roundId = jdbc.queryForObject("SELECT id FROM round WHERE code = 'GROUP'", Long.class);
    jdbc.update(
        "INSERT INTO team (id, tournament_id, code, name) VALUES "
            + "(7001,1,'AAA','Team A'),(7002,1,'BBB','Team B') ON CONFLICT (id) DO NOTHING");
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, "
            + " score_t1, score_t2, played, kickoff_at) "
            + "VALUES (8001, 1, ?, 'A', 7001, 7002, 2, 1, TRUE, now() - interval '3 hours')",
        roundId);

    // API now reports a DIFFERENT score for the same match.
    MatchApi changed =
        new MatchApi(
            8001L,
            "2026-06-11T17:00:00Z",
            "FINISHED",
            "GROUP_STAGE",
            "Group A",
            new MatchTeam(7001L, "Team A"),
            new MatchTeam(7002L, "Team B"),
            new MatchScore("HOME_TEAM", "REGULAR", new MatchScoreFull(5, 0), null));

    sync.upsertMatches(new CompetitionMatchesResponse(List.of(changed)));

    // Frozen: the already-played row is unchanged.
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 8001", Integer.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT score_t2 FROM match WHERE id = 8001", Integer.class))
        .isEqualTo(1);
    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 8001", Boolean.class))
        .isTrue();
  }

  @Test
  void appliesResultToANotYetPlayedMatch() {
    Long roundId = jdbc.queryForObject("SELECT id FROM round WHERE code = 'GROUP'", Long.class);
    jdbc.update(
        "INSERT INTO team (id, tournament_id, code, name) VALUES "
            + "(7003,1,'CCC','Team C'),(7004,1,'DDD','Team D') ON CONFLICT (id) DO NOTHING");
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, "
            + " played, kickoff_at) "
            + "VALUES (8002, 1, ?, 'A', 7003, 7004, FALSE, now() - interval '3 hours')",
        roundId);

    MatchApi finished =
        new MatchApi(
            8002L,
            "2026-06-11T17:00:00Z",
            "FINISHED",
            "GROUP_STAGE",
            "Group A",
            new MatchTeam(7003L, "Team C"),
            new MatchTeam(7004L, "Team D"),
            new MatchScore("HOME_TEAM", "REGULAR", new MatchScoreFull(3, 0), null));

    sync.upsertMatches(new CompetitionMatchesResponse(List.of(finished)));

    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 8002", Boolean.class))
        .isTrue();
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 8002", Integer.class))
        .isEqualTo(3);
  }
}
