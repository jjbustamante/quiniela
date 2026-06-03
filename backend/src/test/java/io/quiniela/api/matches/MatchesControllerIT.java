package io.quiniela.api.matches;

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

class MatchesControllerIT extends AbstractIntegrationTest {

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
  void restoreMatch1() {
    // Restore V006 seed values that some tests mutate so other suites see clean state.
    jdbc.update(
        "UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE, winner_id = NULL,"
            + " advanced_team_id = NULL,"
            + " kickoff_at = TIMESTAMPTZ '2026-06-11 17:00 UTC' WHERE id = 1");
  }

  @Test
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/matches")).andExpect(status().isUnauthorized());
  }

  @Test
  void allFutureMatchesLandInUpcomingByDefault() throws Exception {
    var u = saveUser("mt-default", "Default Caller");
    String token = jwt.issue(u);

    // V006 seeds 104 matches starting 2026-06-11; in real time we're before all of them.
    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.past.length()").value(0))
        .andExpect(jsonPath("$.today.length()").value(0))
        .andExpect(jsonPath("$.upcoming.length()").value(104))
        .andExpect(jsonPath("$.upcoming[0].team1.code").exists())
        .andExpect(jsonPath("$.upcoming[0].team1.flag").exists())
        .andExpect(jsonPath("$.upcoming[0].yourPick").doesNotExist())
        .andExpect(jsonPath("$.upcoming[0].pointsEarned").doesNotExist());
  }

  @Test
  void playedMatchSurfacesPointsEarnedFromTrigger() throws Exception {
    var u = saveUser("mt-played", "Played Caller");
    String token = jwt.issue(u);

    // Place a bet via the same path the app uses (auto-creates the quiniela).
    insertBet(u.getId(), 1L, 2, 1);

    // Push match #1 into the past so it lands in past[], then record the actual result.
    jdbc.update(
        "UPDATE match SET kickoff_at = NOW() - INTERVAL '2 days', score_t1 = 2,"
            + " score_t2 = 1, played = TRUE WHERE id = 1");

    // Bet 2-1, actual 2-1 → V010 awards 3 (outcome) + 2 (t1 exact) + 2 (t2 exact) = 7 (group).
    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.past.length()").value(1))
        .andExpect(jsonPath("$.past[0].played").value(true))
        .andExpect(jsonPath("$.past[0].score.t1").value(2))
        .andExpect(jsonPath("$.past[0].score.t2").value(1))
        .andExpect(jsonPath("$.past[0].yourPick.t1").value(2))
        .andExpect(jsonPath("$.past[0].yourPick.t2").value(1))
        .andExpect(jsonPath("$.past[0].pointsEarned").value(7));
  }

  @Test
  void drawPickSurfacesPredictedAdvancingTeam() throws Exception {
    var u = saveUser("mt-draw", "Draw Caller");
    String token = jwt.issue(u);

    Long team2Id = jdbc.queryForObject("SELECT team_2_id FROM match WHERE id = 1", Long.class);

    // Bet a draw 1-1 and predict team2 advances on penalties. predicted_winner_id
    // lives on the bet, so (unlike match.winner_id) it survives a level pick.
    Long quinielaId =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points, created_at, updated_at)"
                + " VALUES (1, ?, 0, NOW(), NOW()) RETURNING id",
            Long.class,
            u.getId());
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, predicted_winner_id,"
            + " created_at, updated_at) VALUES (?, 1, 1, 1, ?, NOW(), NOW())",
        quinielaId,
        team2Id);

    // Actual result is decisive (knockouts never store a draw); push to the past.
    jdbc.update(
        "UPDATE match SET kickoff_at = NOW() - INTERVAL '2 days', score_t1 = 2, score_t2 = 1,"
            + " played = TRUE WHERE id = 1");

    String pickCode =
        jdbc.queryForObject("SELECT code FROM team WHERE id = ?", String.class, team2Id);

    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.past[0].yourPick.t1").value(1))
        .andExpect(jsonPath("$.past[0].yourPick.t2").value(1))
        .andExpect(jsonPath("$.past[0].pickWinner.code").value(pickCode))
        .andExpect(jsonPath("$.past[0].pickWinner.flag").exists());
  }

  @Test
  void drawResultSurfacesActualAdvancingTeam() throws Exception {
    var u = saveUser("mt-adv", "Advancing Caller");
    String token = jwt.issue(u);

    Long team2Id = jdbc.queryForObject("SELECT team_2_id FROM match WHERE id = 1", Long.class);

    // 1-1 result, team2 advanced (penalties). Push to the past.
    jdbc.update(
        "UPDATE match SET kickoff_at = NOW() - INTERVAL '2 days', score_t1 = 1, score_t2 = 1,"
            + " played = TRUE, advanced_team_id = ? WHERE id = 1",
        team2Id);

    String advCode =
        jdbc.queryForObject("SELECT code FROM team WHERE id = ?", String.class, team2Id);

    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.past[0].winner.code").value(advCode))
        .andExpect(jsonPath("$.past[0].winner.flag").exists());
  }

  @Test
  void matchInTodayWindowLandsInTodayBucket() throws Exception {
    var u = saveUser("mt-today", "Today Caller");
    String token = jwt.issue(u);

    // Pin match #1's kickoff to right now → today's UTC bucket.
    jdbc.update("UPDATE match SET kickoff_at = NOW() WHERE id = 1");

    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.today.length()").value(1))
        .andExpect(jsonPath("$.today[0].id").value(1));
  }

  private User saveUser(String slug, String displayName) {
    var u = new User("g-" + slug, slug + "@example.com", displayName, null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    return users.save(u);
  }

  private void insertBet(Long userId, Long matchId, int t1, int t2) {
    Long quinielaId =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points, created_at, updated_at)"
                + " VALUES (1, ?, 0, NOW(), NOW()) RETURNING id",
            Long.class,
            userId);
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, created_at, updated_at)"
            + " VALUES (?, ?, ?, ?, NOW(), NOW())",
        quinielaId,
        matchId,
        t1,
        t2);
  }
}
