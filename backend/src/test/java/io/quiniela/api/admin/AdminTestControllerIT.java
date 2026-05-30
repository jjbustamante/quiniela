package io.quiniela.api.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.auth.JwtService;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AdminTestControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;
  @Autowired io.quiniela.api.admin.AdminTestService adminTestService;

  MockMvc mockMvc;
  JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);
    jdbc.update("UPDATE tournament SET test_mode = true WHERE id = 1");
  }

  @AfterEach
  void restoreTestMode() {
    jdbc.update("UPDATE tournament SET test_mode = true WHERE id = 1");
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    jdbc.update(
        "UPDATE match m SET team_1_id=NULL, team_2_id=NULL "
            + "WHERE m.tournament_id=1 AND m.match_parent_1_id IS NOT NULL");
  }

  private String adminToken() {
    var u = new User("g-admintest", "admintest@example.com", "Admin T", null, UserRole.ADMIN);
    u.setInvitePath("admintest-abc");
    u = users.save(u);
    return jwt.issue(u);
  }

  private String playerToken() {
    var u = new User("g-playertest", "playertest@example.com", "Player T", null, UserRole.PLAYER);
    u.setInvitePath("playertest-abc");
    u = users.save(u);
    return jwt.issue(u);
  }

  @Test
  void stateRequiresAdmin() throws Exception {
    mockMvc
        .perform(get("/api/admin/test/state").header("Authorization", "Bearer " + playerToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void stateReturnsTestModeAndCurrentRound() throws Exception {
    mockMvc
        .perform(get("/api/admin/test/state").header("Authorization", "Bearer " + adminToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testMode").value(true))
        .andExpect(jsonPath("$.currentRoundCode").value("GROUP"));
  }

  @Test
  void modeToggleWorksForAdmin() throws Exception {
    String token = adminToken();
    mockMvc
        .perform(
            put("/api/admin/test/mode")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testMode").value(false));
    mockMvc
        .perform(
            put("/api/admin/test/mode")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.testMode").value(true));
  }

  @Test
  void cleanResetsBetsResultsPointsPaymentsButKeepsFixtures() throws Exception {
    String token = adminToken();
    var player = new User("g-cleanme", "cleanme@example.com", "Clean", null, UserRole.PLAYER);
    player.setInvitePath("cleanme-abc");
    player = users.save(player);
    jdbc.update("UPDATE match SET score_t1=2, score_t2=1, played=true WHERE id=1");
    Long qid =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points, created_at, updated_at) "
                + "VALUES (1, ?, 7, NOW(), NOW()) RETURNING id",
            Long.class,
            player.getId());
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, created_at, updated_at) "
            + "VALUES (?, 1, 2, 1, NOW(), NOW())",
        qid);
    jdbc.update(
        "INSERT INTO payment (pool_id, user_id, paid, paid_at, marked_paid_by, created_at, updated_at) "
            + "VALUES (1, ?, true, NOW(), ?, NOW(), NOW())",
        player.getId(),
        player.getId());

    long teamsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM team", Long.class);

    mockMvc
        .perform(post("/api/admin/test/clean").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM bet", Long.class))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT points FROM quiniela WHERE id = ?", Integer.class, qid))
        .isZero();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT played FROM match WHERE id = 1", Boolean.class))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject(
                "SELECT paid FROM payment WHERE pool_id = 1 AND user_id = ?",
                Boolean.class,
                player.getId()))
        .isFalse();
    org.assertj.core.api.Assertions.assertThat(
            jdbc.queryForObject("SELECT COUNT(*) FROM team", Long.class))
        .isEqualTo(teamsBefore);
  }

  @Test
  void cleanIsForbiddenForNonAdmin() throws Exception {
    mockMvc
        .perform(post("/api/admin/test/clean").header("Authorization", "Bearer " + playerToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void cleanReturns409WhenTestModeOff() throws Exception {
    String token = adminToken();
    jdbc.update("UPDATE tournament SET test_mode = false WHERE id = 1");
    mockMvc
        .perform(post("/api/admin/test/clean").header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
  }

  @Test
  void simulateRoundPlaysCurrentRoundAndScores() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");

    long groupUnplayedBefore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.played=false",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(groupUnplayedBefore).isGreaterThan(0);

    mockMvc
        .perform(post("/api/admin/test/simulate/round").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundCode").value("GROUP"))
        .andExpect(jsonPath("$.matchesPlayed").value((int) groupUnplayedBefore));

    long groupUnplayedAfter =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.played=false",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(groupUnplayedAfter).isZero();
  }

  @Test
  void simulateRoundIsForbiddenForNonAdmin() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/test/simulate/round")
                .header("Authorization", "Bearer " + playerToken()))
        .andExpect(status().isForbidden());
  }

  @Test
  void simulateRoundReturns409WhenTestModeOff() throws Exception {
    String token = adminToken();
    jdbc.update("UPDATE tournament SET test_mode = false WHERE id = 1");
    mockMvc
        .perform(post("/api/admin/test/simulate/round").header("Authorization", "Bearer " + token))
        .andExpect(status().isConflict());
  }

  @Test
  void simulateAllPlaysEveryResolvableMatch() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");

    mockMvc
        .perform(post("/api/admin/test/simulate/all").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundsSimulated").isNumber());

    long groupUnplayed =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.played=false",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(groupUnplayed).isZero();
  }

  @Test
  void groupStandingsRanksByPointsThenGoalDiffThenGoalsForThenId() {
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    var ids =
        jdbc.queryForList(
            "SELECT m.id FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.group_code='A' "
                + "ORDER BY m.kickoff_at ASC",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(ids).hasSize(6);
    var teamIds =
        jdbc.queryForList(
            "SELECT DISTINCT t.id FROM team t WHERE t.tournament_id=1 AND t.group_code='A' ORDER BY t.id",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(teamIds).hasSize(4);

    for (Long id : ids) {
      jdbc.update(
          "UPDATE match SET score_t1=1, score_t2=0, winner_id=team_1_id, played=true WHERE id=?",
          id);
    }

    var standings = adminTestService.groupStandings();
    org.assertj.core.api.Assertions.assertThat(standings).containsKey("A");
    var groupA = standings.get("A");
    org.assertj.core.api.Assertions.assertThat(groupA).hasSize(4);
    for (int i = 0; i + 1 < groupA.size(); i++) {
      var hi = groupA.get(i);
      var lo = groupA.get(i + 1);
      boolean ordered =
          hi.points() > lo.points()
              || (hi.points() == lo.points() && hi.goalDiff() > lo.goalDiff())
              || (hi.points() == lo.points()
                  && hi.goalDiff() == lo.goalDiff()
                  && hi.goalsFor() > lo.goalsFor())
              || (hi.points() == lo.points()
                  && hi.goalDiff() == lo.goalDiff()
                  && hi.goalsFor() == lo.goalsFor()
                  && hi.teamId() < lo.teamId());
      org.assertj.core.api.Assertions.assertThat(ordered)
          .as("standings entry %d ordered before %d", i, i + 1)
          .isTrue();
    }
  }

  @Test
  void simulatingGroupSeedsR32AndWiresBracket() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    jdbc.update(
        "UPDATE match m SET team_1_id=NULL, team_2_id=NULL, match_parent_1_id=NULL, match_parent_2_id=NULL "
            + "WHERE m.tournament_id=1 AND m.round_id <> "
            + "(SELECT id FROM round WHERE tournament_id=1 AND code='GROUP')");

    mockMvc
        .perform(post("/api/admin/test/simulate/round").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundCode").value("GROUP"));

    Long r32WithoutTeams =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='R32' AND (m.team_1_id IS NULL OR m.team_2_id IS NULL)",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(r32WithoutTeams).isZero();

    Long r32Count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='R32'",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(r32Count).isEqualTo(16L);

    Long childrenWithoutParents =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code IN ('R16','QF','SF','FINAL','THIRD_PLACE') "
                + "AND (m.match_parent_1_id IS NULL OR m.match_parent_2_id IS NULL)",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(childrenWithoutParents).isZero();

    Long totalR32Slots =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM ("
                + "  SELECT team_1_id AS t FROM match m JOIN round r ON r.id=m.round_id "
                + "    WHERE m.tournament_id=1 AND r.code='R32' "
                + "  UNION ALL "
                + "  SELECT team_2_id FROM match m JOIN round r ON r.id=m.round_id "
                + "    WHERE m.tournament_id=1 AND r.code='R32') x",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(totalR32Slots).isEqualTo(32L);
    Long uniqueR32Teams =
        jdbc.queryForObject(
            "SELECT COUNT(DISTINCT t) FROM ("
                + "  SELECT team_1_id AS t FROM match m JOIN round r ON r.id=m.round_id "
                + "    WHERE m.tournament_id=1 AND r.code='R32' "
                + "  UNION ALL "
                + "  SELECT team_2_id FROM match m JOIN round r ON r.id=m.round_id "
                + "    WHERE m.tournament_id=1 AND r.code='R32') x",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(uniqueR32Teams).isEqualTo(32L);
  }

  @Test
  void simulateAllReachesAChampionAndThirdPlaceGetsSfLosers() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    jdbc.update(
        "UPDATE match m SET team_1_id=NULL, team_2_id=NULL, match_parent_1_id=NULL, match_parent_2_id=NULL "
            + "WHERE m.tournament_id=1 AND m.round_id <> "
            + "(SELECT id FROM round WHERE tournament_id=1 AND code='GROUP')");

    mockMvc
        .perform(post("/api/admin/test/simulate/all").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    Boolean finalPlayed =
        jdbc.queryForObject(
            "SELECT m.played FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='FINAL'",
            Boolean.class);
    org.assertj.core.api.Assertions.assertThat(finalPlayed).isTrue();
    Long finalWinner =
        jdbc.queryForObject(
            "SELECT m.winner_id FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='FINAL'",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(finalWinner).isNotNull();

    var sfRows =
        jdbc.queryForList(
            "SELECT m.team_1_id AS t1, m.team_2_id AS t2, m.winner_id AS w "
                + "FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='SF' ORDER BY m.kickoff_at ASC");
    org.assertj.core.api.Assertions.assertThat(sfRows).hasSize(2);
    java.util.Set<Long> expectedLosers = new java.util.HashSet<>();
    for (var row : sfRows) {
      long t1 = ((Number) row.get("t1")).longValue();
      long t2 = ((Number) row.get("t2")).longValue();
      long w = ((Number) row.get("w")).longValue();
      expectedLosers.add(w == t1 ? t2 : t1);
    }
    var thirdTeams =
        jdbc.queryForList(
            "SELECT team_1_id FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='THIRD_PLACE' "
                + "UNION ALL "
                + "SELECT team_2_id FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='THIRD_PLACE'",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(new java.util.HashSet<>(thirdTeams))
        .isEqualTo(expectedLosers);
  }

  @Test
  void cleanResetsSimulatedBracketButKeepsGroupTeams() throws Exception {
    String token = adminToken();
    jdbc.update(
        "UPDATE match SET score_t1=NULL, score_t2=NULL, winner_id=NULL, played=false WHERE tournament_id=1");
    mockMvc
        .perform(post("/api/admin/test/simulate/all").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    long groupTeamsBefore =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.team_1_id IS NOT NULL",
            Long.class);

    mockMvc
        .perform(post("/api/admin/test/clean").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());

    Long koWithTeamsOrParents =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code <> 'GROUP' "
                + "AND (m.team_1_id IS NOT NULL OR m.team_2_id IS NOT NULL "
                + "     OR m.match_parent_1_id IS NOT NULL OR m.match_parent_2_id IS NOT NULL)",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(koWithTeamsOrParents).isZero();

    long groupTeamsAfter =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id=m.round_id "
                + "WHERE m.tournament_id=1 AND r.code='GROUP' AND m.team_1_id IS NOT NULL",
            Long.class);
    org.assertj.core.api.Assertions.assertThat(groupTeamsAfter).isEqualTo(groupTeamsBefore);
  }

  @Test
  void deadlinesUpdateInTestMode() throws Exception {
    String token = adminToken();
    mockMvc
        .perform(
            put("/api/admin/test/deadlines")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"groupStageDeadline\":\"2020-01-01T00:00:00Z\",\"knockoutDeadline\":\"2020-02-01T00:00:00Z\"}"))
        .andExpect(status().isOk());
    String gs =
        jdbc.queryForObject(
            "SELECT TO_CHAR(group_stage_deadline AT TIME ZONE 'UTC', 'YYYY-MM-DD') FROM tournament WHERE id = 1",
            String.class);
    org.assertj.core.api.Assertions.assertThat(gs).startsWith("2020-01-01");
  }
}
