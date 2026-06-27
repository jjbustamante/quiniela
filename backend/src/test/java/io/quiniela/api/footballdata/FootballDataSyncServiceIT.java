package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.quiniela.api.footballdata.FootballDataClient.CompetitionMatchesResponse;
import io.quiniela.api.footballdata.FootballDataClient.MatchApi;
import io.quiniela.api.footballdata.FootballDataClient.MatchScore;
import io.quiniela.api.footballdata.FootballDataClient.MatchScoreFull;
import io.quiniela.api.footballdata.FootballDataClient.MatchTeam;
import io.quiniela.api.support.AbstractIntegrationTest;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@Import(FootballDataSyncServiceIT.Stubs.class)
@TestPropertySource(properties = {"app.football-data.enabled=true"})
class FootballDataSyncServiceIT extends AbstractIntegrationTest {

  // Mutable holder so each test sets the matches the stubbed client returns.
  static volatile CompetitionMatchesResponse stubbed = new CompetitionMatchesResponse(List.of());
  // Self-resetting failure flag: set to true to make the next getMatches call throw once.
  static volatile boolean failNextGet = false;

  @TestConfiguration
  static class Stubs {
    @Bean
    @Primary
    FootballDataClient stubClient() {
      return new FootballDataClient("http://unused", "x") {
        @Override
        public CompetitionMatchesResponse getMatches(String code) {
          if (failNextGet) {
            failNextGet = false;
            throw new RuntimeException("simulated api error");
          }
          return stubbed;
        }
      };
    }

    @Bean
    @Primary
    FakeResultsTaskQueue fakeQueue() {
      return new FakeResultsTaskQueue();
    }
  }

  @Autowired FootballDataSyncService sync;
  @Autowired FakeResultsTaskQueue queue;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seedTeams() {
    jdbc.update(
        "INSERT INTO team (id, tournament_id, code, name) VALUES "
            + "(6001,1,'TQH','Home'),(6002,1,'TQA','Away') ON CONFLICT (id) DO NOTHING");
  }

  @AfterEach
  void cleanTestMatches() {
    // Remove this test class's transient rows to keep the DB clean for other test classes.
    jdbc.update("DELETE FROM match WHERE id >= 6100 AND id <= 6299");
    jdbc.update("DELETE FROM team WHERE id IN (6001, 6002)");
  }

  private Long groupRound() {
    return jdbc.queryForObject("SELECT id FROM round WHERE code = 'GROUP'", Long.class);
  }

  private static MatchApi match(long id, String status, Integer h, Integer a) {
    return new MatchApi(
        id,
        "2026-06-11T17:00:00Z",
        status,
        "GROUP_STAGE",
        "Group A",
        new MatchTeam(6001L, "H"),
        new MatchTeam(6002L, "A"),
        new MatchScore(
            h == null ? null : (h > a ? "HOME_TEAM" : "AWAY_TEAM"),
            "REGULAR",
            new MatchScoreFull(h, a),
            null));
  }

  @Test
  void planTodayEnqueuesOneTaskPerUpcomingUnplayedMatch() {
    queue.calls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6101, 1, ?, 'A', 6001, 6002, FALSE, now() + interval '2 hours'), "
            + "       (6102, 1, ?, 'A', 6001, 6002, TRUE,  now() + interval '3 hours'), " // played
            // → skip
            + "       (6103, 1, ?, 'A', 6001, 6002, FALSE, now() + interval '40 hours')", // >24h →
        // skip
        groupRound(),
        groupRound(),
        groupRound());

    sync.planToday();

    assertThat(queue.calls)
        .extracting(FakeResultsTaskQueue.Enqueued::matchId)
        .contains(6101L) // upcoming unplayed → enqueued
        .doesNotContain(6102L, 6103L); // played and >24h → not enqueued

    // firstPollOffsetMinutes=0 → first poll scheduled at kickoff (within 5s tolerance)
    java.sql.Timestamp kickoffTs =
        jdbc.queryForObject(
            "SELECT kickoff_at FROM match WHERE id = 6101", java.sql.Timestamp.class);
    java.time.Instant kickoff = kickoffTs.toInstant();
    assertThat(queue.calls)
        .filteredOn(c -> c.matchId() == 6101L)
        .singleElement()
        .satisfies(c -> assertThat(c.when()).isCloseTo(kickoff, within(5, ChronoUnit.SECONDS)));
  }

  @Test
  void syncMatchAppliesFinishedResultAndDoesNotReEnqueue() {
    queue.calls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6201, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6201, "FINISHED", 2, 0)));

    sync.syncMatch(6201L);

    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 6201", Boolean.class))
        .isTrue();
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 6201", Integer.class))
        .isEqualTo(2);
    assertThat(queue.calls).isEmpty(); // got the result → no re-enqueue
  }

  @Test
  void syncMatchReEnqueuesWhenNotYetFinishedWithinWindow() {
    queue.calls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6202, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6202, "IN_PLAY", null, null)));

    java.time.Instant before = java.time.Instant.now();
    sync.syncMatch(6202L);
    java.time.Instant after = java.time.Instant.now();

    assertThat(jdbc.queryForObject("SELECT played FROM match WHERE id = 6202", Boolean.class))
        .isFalse();
    // retryIntervalMinutes=5 → re-enqueued ~5 min from now
    assertThat(queue.calls)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.matchId()).isEqualTo(6202L);
              java.time.Instant expectedNext = before.plus(java.time.Duration.ofMinutes(5));
              java.time.Instant expectedNextHigh = after.plus(java.time.Duration.ofMinutes(5));
              assertThat(c.when()).isBetween(expectedNext, expectedNextHigh);
            });
  }

  @Test
  void syncMatchIsNoOpForAlreadyPlayedMatch() {
    queue.calls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, score_t1, score_t2, played, kickoff_at) "
            + "VALUES (6203, 1, ?, 'A', 6001, 6002, 1, 0, TRUE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6203, "FINISHED", 4, 4)));

    sync.syncMatch(6203L);

    // Frozen, and no re-enqueue.
    assertThat(jdbc.queryForObject("SELECT score_t1 FROM match WHERE id = 6203", Integer.class))
        .isEqualTo(1);
    assertThat(queue.calls).isEmpty();
  }

  @Test
  void syncMatchSchedulesTailRefreshOnFinal() {
    queue.calls.clear();
    queue.fixturesCalls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6210, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6210, "FINISHED", 1, 0)));

    sync.syncMatch(6210L);

    assertThat(queue.calls).isEmpty(); // no per-match result re-enqueue
    assertThat(queue.fixturesCalls)
        .isNotEmpty()
        .allSatisfy(c -> assertThat(c.dedupName()).startsWith("fixtures-"));
  }

  @Test
  void syncMatchDoesNotScheduleTailWhenStillUnplayed() {
    queue.calls.clear();
    queue.fixturesCalls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6211, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    stubbed = new CompetitionMatchesResponse(List.of(match(6211, "IN_PLAY", null, null)));

    sync.syncMatch(6211L);

    assertThat(queue.fixturesCalls).isEmpty(); // still unplayed → no tail
  }

  @Test
  void syncMatchDoesNotScheduleTailOnApiError() {
    queue.calls.clear();
    queue.fixturesCalls.clear();
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, group_code, team_1_id, team_2_id, played, kickoff_at) "
            + "VALUES (6212, 1, ?, 'A', 6001, 6002, FALSE, now() - interval '2 hours')",
        groupRound());
    failNextGet = true;

    sync.syncMatch(6212L);

    assertThat(queue.fixturesCalls).isEmpty(); // api error → no tail scheduled
    assertThat(queue.calls).isNotEmpty(); // api error path re-enqueues the per-match result check
    failNextGet = false; // defensive reset in case of partial runs
  }
}
