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
    // Restore seed values that some tests mutate so other suites see clean state.
    // Use a NOW()-relative future kickoff (matching V021's re-anchor) so match 1
    // returns to the "upcoming" bucket regardless of the calendar date the suite
    // runs on — an absolute date here becomes a past-bucket pollutant once it passes.
    jdbc.update(
        "UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE, winner_id = NULL,"
            + " advanced_team_id = NULL,"
            + " kickoff_at = NOW() + INTERVAL '10 days' WHERE id = 1");
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

  @Test
  void bucketsByCallersTimezoneNotUtc() throws Exception {
    // The default user's timezone is America/Bogota (UTC-5).
    var u = saveUser("mt-tz", "TZ Caller");
    String token = jwt.issue(u);

    // 1h before midnight TODAY in Bogotá = late "yesterday" in Bogotá, yet still the same UTC
    // calendar day (e.g. 23:00 Bogotá = 04:00 UTC). This is exactly the screenshot bug: under
    // UTC bucketing it wrongly shows under "Today"; it must bucket as PAST in the caller's zone.
    jdbc.update(
        "UPDATE match SET kickoff_at ="
            + " (DATE_TRUNC('day', NOW() AT TIME ZONE 'America/Bogota') AT TIME ZONE 'America/Bogota')"
            + " - INTERVAL '1 hour' WHERE id = 1");

    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.past[?(@.id == 1)].id").value(org.hamcrest.Matchers.hasItem(1)))
        .andExpect(
            jsonPath("$.today[?(@.id == 1)].id")
                .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(1))));
  }

  @Test
  void knockoutDrawWrongPredictedWinnerDoesNotGetOutcomeBonusInPointsEarned() throws Exception {
    var u = saveUser("mt-kowrong", "KO Wrong Caller");
    String token = jwt.issue(u);

    Long koId =
        jdbc.queryForObject(
            "SELECT m.id FROM match m JOIN round r ON r.id = m.round_id"
                + " WHERE r.code = 'R32' ORDER BY m.id LIMIT 1",
            Long.class);
    var teamIds = jdbc.queryForList("SELECT id FROM team ORDER BY id LIMIT 2", Long.class);
    Long team1 = teamIds.get(0);
    Long team2 = teamIds.get(1);
    // Capture the seeded kickoff so we can restore it — otherwise this R32 match keeps
    // a past kickoff + null teams and sorts to the top of other suites' match listings.
    java.sql.Timestamp originalKickoff =
        jdbc.queryForObject(
            "SELECT kickoff_at FROM match WHERE id = ?", java.sql.Timestamp.class, koId);
    jdbc.update(
        "UPDATE match SET team_1_id = ?, team_2_id = ?, kickoff_at = NOW() - INTERVAL '2 days'"
            + " WHERE id = ?",
        team1,
        team2,
        koId);

    // Bet 1-1 predicting team1 advances; auto-create the quiniela.
    Long quinielaId =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points, created_at, updated_at)"
                + " VALUES (1, ?, 0, NOW(), NOW()) RETURNING id",
            Long.class,
            u.getId());
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, predicted_winner_id,"
            + " created_at, updated_at) VALUES (?, ?, 1, 1, ?, NOW(), NOW())",
        quinielaId,
        koId,
        team1);

    // Actual 1-1, team2 advanced (penalties) -> the pick (team1) is WRONG.
    jdbc.update(
        "UPDATE match SET score_t1 = 1, score_t2 = 1, played = TRUE, advanced_team_id = ?"
            + " WHERE id = ?",
        team2,
        koId);

    try {
      mockMvc
          .perform(get("/api/matches").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          // exact 1-1 on a knockout = (2 + 2) * 2 = 8; NO +3 outcome bonus (wrong advancing pick).
          .andExpect(
              jsonPath("$.past[?(@.id == " + koId + ")].pointsEarned")
                  .value(org.hamcrest.Matchers.contains(8)));
    } finally {
      jdbc.update(
          "UPDATE match SET team_1_id = NULL, team_2_id = NULL, score_t1 = NULL, score_t2 = NULL,"
              + " played = FALSE, winner_id = NULL, advanced_team_id = NULL, kickoff_at = ?"
              + " WHERE id = ?",
          originalKickoff,
          koId);
    }
  }

  @Test
  void playedBetMatchExposesAPointsBreakdown() throws Exception {
    var u = saveUser("mb", "MB Caller");
    insertBet(u.getId(), 1L, 2, 1);
    jdbc.update(
        "UPDATE match SET kickoff_at = NOW() - INTERVAL '2 days', score_t1 = 2,"
            + " score_t2 = 1, played = TRUE WHERE id = 1");

    String token = jwt.issue(u);
    mockMvc
        .perform(get("/api/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        // match 1 (bet 2-1, actual 2-1, group) → outcome 3 + each-team 2 = total 7.
        .andExpect(
            jsonPath("$.past[?(@.id == 1)].breakdown.outcome")
                .value(org.hamcrest.Matchers.hasItem(3)))
        .andExpect(
            jsonPath("$.past[?(@.id == 1)].breakdown.total")
                .value(org.hamcrest.Matchers.hasItem(7)))
        .andExpect(
            jsonPath("$.past[?(@.id == 1)].pointsEarned").value(org.hamcrest.Matchers.hasItem(7)));
  }

  @Test
  void liveMatchSurfacesLiveFlagAndProvisionalPoints() throws Exception {
    var u = saveUser("mt-live", "Live Caller");
    String token = jwt.issue(u);

    // Place a bet on match #1.
    insertBet(u.getId(), 1L, 2, 1);

    // Seed the match as LIVE: score present but played=FALSE, kickoff 2 days in the past
    // so it lands in past[] regardless of the caller's timezone (America/Bogota default).
    jdbc.update(
        "UPDATE match SET kickoff_at = NOW() - INTERVAL '2 days',"
            + " score_t1 = 2, score_t2 = 1, played = FALSE WHERE id = 1");

    try {
      mockMvc
          .perform(get("/api/matches").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          // Live match has a past kickoff → lands in past[] bucket.
          .andExpect(
              jsonPath("$.past[?(@.id == 1)].live").value(org.hamcrest.Matchers.hasItem(true)))
          // Provisional points: bet 2-1 vs live 2-1, group → 7 points.
          .andExpect(
              jsonPath("$.past[?(@.id == 1)].pointsEarned")
                  .value(org.hamcrest.Matchers.hasItem(7)));
    } finally {
      jdbc.update(
          "UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE,"
              + " kickoff_at = NOW() + INTERVAL '10 days' WHERE id = 1");
    }
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
