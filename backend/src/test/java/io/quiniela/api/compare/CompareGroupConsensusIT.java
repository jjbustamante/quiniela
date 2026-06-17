package io.quiniela.api.compare;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class CompareGroupConsensusIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);
  }

  @AfterEach
  void restoreDeadlines() {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC',"
            + " knockout_deadline = TIMESTAMPTZ '2026-06-28 17:00 UTC' WHERE id = 1");
    // Match 73 (R32 knockout) is reference data not reset by cleanWritableTables; the
    // played-knockout test below mutates it, so restore it to unplayed.
    jdbc.update(
        "UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE,"
            + " advanced_team_id = NULL WHERE id = 73");
  }

  private String userWithBetOnMatch1(String slug, int t1, int t2) {
    return userWithBetOnMatch(slug, 1L, t1, t2);
  }

  private String userWithBetOnMatch(String slug, Long matchId, int t1, int t2) {
    var u =
        new User("g-" + slug, slug + "@example.com", slug.toUpperCase(), null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    u = users.save(u);
    jdbc.update("INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?)", u.getId());
    Long qid =
        jdbc.queryForObject("SELECT id FROM quiniela WHERE user_id = ?", Long.class, u.getId());
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2) VALUES (?,?,?,?)",
        qid,
        matchId,
        t1,
        t2);
    return jwt.issue(u);
  }

  @Test
  void revealsAPlayedKnockoutMatchEvenBeforeTheKnockoutDeadline() throws Exception {
    // Knockout deadline still in the future, but the match has been played (test-mode
    // simulation). A played match can't be bet on anymore, so its picks are safe to
    // reveal — Compare must show it instead of staying group-only.
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour',"
            + " knockout_deadline = NOW() + INTERVAL '7 days' WHERE id = 1");
    jdbc.update("UPDATE match SET score_t1 = 1, score_t2 = 0, played = TRUE WHERE id = 73");
    String me = userWithBetOnMatch("ko-played-me", 73L, 2, 1);
    userWithBetOnMatch("ko-played-r1", 73L, 2, 1);

    mockMvc
        .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$..[?(@.matchId == 73)].revealed").value(org.hamcrest.Matchers.hasItem(true)))
        .andExpect(
            jsonPath("$..[?(@.matchId == 73)].totalPicks").value(org.hamcrest.Matchers.hasItem(2)));
  }

  @Test
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/compare/group")).andExpect(status().isUnauthorized());
  }

  @Test
  void hidesDistributionBeforeGroupLock() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() + INTERVAL '7 days' WHERE id = 1");
    String me = userWithBetOnMatch1("grp-pre-me", 2, 1);
    userWithBetOnMatch1("grp-pre-rival", 0, 0);

    mockMvc
        .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].revealed").value(org.hamcrest.Matchers.hasItem(false)))
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].distribution")
                .value(org.hamcrest.Matchers.hasItem(java.util.Collections.emptyList())))
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].myScoreT1").value(org.hamcrest.Matchers.hasItem(2)));
  }

  @Test
  void revealsConsensusAfterGroupLock() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    String me = userWithBetOnMatch1("grp-post-me", 2, 1);
    userWithBetOnMatch1("grp-post-r1", 2, 1);
    userWithBetOnMatch1("grp-post-r2", 0, 0);

    mockMvc
        .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.serverTime").exists())
        .andExpect(jsonPath("$.past").isArray())
        .andExpect(jsonPath("$.today").isArray())
        .andExpect(jsonPath("$.upcoming").isArray())
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].totalPicks").value(org.hamcrest.Matchers.hasItem(3)))
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].majority").value(org.hamcrest.Matchers.hasItem(true)))
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].rebel").value(org.hamcrest.Matchers.hasItem(false)));
  }

  @Test
  void flagsRebelWhenOnlyPickerOfAScore() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    String me = userWithBetOnMatch1("grp-rebel-me", 4, 4);
    userWithBetOnMatch1("grp-rebel-r1", 1, 0);
    userWithBetOnMatch1("grp-rebel-r2", 1, 0);

    mockMvc
        .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].majority").value(org.hamcrest.Matchers.hasItem(false)))
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].rebel").value(org.hamcrest.Matchers.hasItem(true)));
  }

  @Test
  void returnsNamedPicksRankedForARevealedMatch() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    String me = userWithBetOnMatch1("pk-me", 2, 1);
    String top = userWithBetOnMatch1("pk-top", 1, 1);
    jdbc.update(
        "UPDATE quiniela SET points = 99 WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        "pk-top@example.com");

    mockMvc
        .perform(get("/api/compare/match/1/picks").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchId").value(1))
        .andExpect(jsonPath("$.picks.length()").value(2))
        // ranked by points DESC: the 99-point rival is first and is flagged above me
        .andExpect(jsonPath("$.picks[0].isAboveMe").value(true))
        .andExpect(jsonPath("$.picks[0].scoreT1").value(1))
        .andExpect(
            jsonPath("$.picks[?(@.isYou == true)].scoreT1")
                .value(org.hamcrest.Matchers.hasItem(2)));
  }

  @Test
  void forbidsPicksForAnUnrevealedMatch() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() + INTERVAL '7 days' WHERE id = 1");
    String me = userWithBetOnMatch1("pk-hidden-me", 2, 1);

    mockMvc
        .perform(get("/api/compare/match/1/picks").header("Authorization", "Bearer " + me))
        .andExpect(status().isForbidden());
  }

  @Test
  void picksRequiresAuth() throws Exception {
    mockMvc.perform(get("/api/compare/match/1/picks")).andExpect(status().isUnauthorized());
  }

  @Test
  void countsRivalsRankedAboveMePerScoreline() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    // Me: pick 2-1, 0 points (bottom).
    String me = userWithBetOnMatch1("rab-me", 2, 1);
    // Two rivals ABOVE me (more points) who also picked 2-1.
    userWithBetOnMatch1("rab-above1", 2, 1);
    userWithBetOnMatch1("rab-above2", 2, 1);
    jdbc.update(
        "UPDATE quiniela SET points = 50 WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        "rab-above1@example.com");
    jdbc.update(
        "UPDATE quiniela SET points = 40 WHERE user_id = (SELECT id FROM users WHERE email = ?)",
        "rab-above2@example.com");
    // One rival BELOW me (0 points) who picked something else.
    userWithBetOnMatch1("rab-below", 0, 0);

    mockMvc
        .perform(get("/api/compare/group").header("Authorization", "Bearer " + me))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].rivalsAboveTotal")
                .value(org.hamcrest.Matchers.hasItem(2)))
        .andExpect(
            jsonPath("$..[?(@.matchId == 1)].rivalsAbovePicked")
                .value(org.hamcrest.Matchers.hasItem(2)))
        // the 2-1 scoreline carries both rivals-above
        .andExpect(
            jsonPath(
                    "$..[?(@.matchId == 1)].distribution[?(@.scoreT1 == 2 && @.scoreT2 == 1)].rivalsAboveCount")
                .value(org.hamcrest.Matchers.hasItem(2)));
  }
}
