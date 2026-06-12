package io.quiniela.api.ranking;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
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

class ScorecardControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  JdbcTemplate jdbc;
  User viewer;
  User target;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);
    viewer = save("sc-viewer");
    target = save("sc-target");
    jdbc.update("INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?)", target.getId());
    Long qid =
        jdbc.queryForObject(
            "SELECT id FROM quiniela WHERE user_id = ?", Long.class, target.getId());
    // Insert bet BEFORE kickoff so the trigger credits it (match 1 kickoff = 2026-06-11 17:00 UTC).
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, created_at, updated_at)"
            + " VALUES (?,1,2,1, '2026-06-11 16:00 UTC', '2026-06-11 16:00 UTC')",
        qid);
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = 1");
  }

  @AfterEach
  void reset() {
    jdbc.update("UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE WHERE id = 1");
  }

  private User save(String slug) {
    var u =
        new User("g-" + slug, slug + "@example.com", slug.toUpperCase(), null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    return users.save(u);
  }

  @Test
  void requiresAuth() throws Exception {
    mockMvc
        .perform(get("/api/ranking/" + target.getId() + "/scorecard"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void returnsAnyPlayersStageBreakdown() throws Exception {
    String token = jwt.issue(viewer);
    mockMvc
        .perform(
            get("/api/ranking/" + target.getId() + "/scorecard")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.userId").value(target.getId()))
        .andExpect(jsonPath("$.stages[?(@.roundCode == 'GROUP')].points").value(7))
        .andExpect(
            jsonPath("$.stages[?(@.roundCode == 'GROUP')].matches[0].breakdown.outcome").value(3))
        .andExpect(
            jsonPath("$.stages[?(@.roundCode == 'GROUP')].matches[0].breakdown.total").value(7));
  }

  @Test
  void notFoundForAUserWithoutAQuiniela() throws Exception {
    String token = jwt.issue(viewer);
    mockMvc
        .perform(
            get("/api/ranking/" + viewer.getId() + "/scorecard")
                .header("Authorization", "Bearer " + token))
        .andExpect(status().isNotFound());
  }

  // ── Task 3: Frozen bet.points + note tests ─────────────────────────────────

  /**
   * Seeds a played GROUP match (kickoff = 3 hours ago) + 3 bets with explicit points/timestamps,
   * then verifies that ScorecardService reads frozen bet.points (not live recompute) and emits the
   * correct note per bet.
   *
   * <p>Bet 1 (clean): created 4h ago (before kickoff), bet 2-1 vs actual 2-1, bet.points = 7.
   * Scorecard shows 7, note = null.
   *
   * <p>Bet 2 (placed-after): created 1h ago (after kickoff), bet 2-1 (would recompute to 7),
   * bet.points = 0. Scorecard shows 0, note = "PLACED_AFTER_KICKOFF".
   *
   * <p>Bet 3 (edited-after): created 4h ago (before kickoff), updated 1h ago (after kickoff), bet
   * 3-0 (recomputes to 3), bet.points = 4. Scorecard shows 4, note = "EDITED_AFTER_KICKOFF".
   */
  @Test
  void frozenPointsAndNotePerCase() throws Exception {
    // ── Seed a fresh match with kickoff 3 hours ago ──────────────────────────
    Long roundId =
        jdbc.queryForObject("SELECT id FROM round WHERE code = 'GROUP' LIMIT 1", Long.class);
    Long team1Id =
        jdbc.queryForObject(
            "SELECT id FROM team WHERE tournament_id = 1 ORDER BY id LIMIT 1", Long.class);
    Long team2Id =
        jdbc.queryForObject(
            "SELECT id FROM team WHERE tournament_id = 1 ORDER BY id OFFSET 1 LIMIT 1", Long.class);

    Long matchId =
        jdbc.queryForObject(
            "INSERT INTO match (tournament_id, round_id, group_code, team_1_id, team_2_id,"
                + " kickoff_at, score_t1, score_t2, played)"
                + " VALUES (1, ?, 'A', ?, ?, NOW() - INTERVAL '3 hours', 2, 1, TRUE)"
                + " RETURNING id",
            Long.class,
            roundId,
            team1Id,
            team2Id);

    // ── Seed 3 users + quinielas ─────────────────────────────────────────────
    Long u1 =
        jdbc.queryForObject(
            "INSERT INTO users (google_sub, email, display_name, role)"
                + " VALUES ('sc-clean-"
                + System.nanoTime()
                + "', 'clean@sc.test', 'Clean', 'player') RETURNING id",
            Long.class);
    Long u2 =
        jdbc.queryForObject(
            "INSERT INTO users (google_sub, email, display_name, role)"
                + " VALUES ('sc-placed-"
                + System.nanoTime()
                + "', 'placed@sc.test', 'Placed', 'player') RETURNING id",
            Long.class);
    Long u3 =
        jdbc.queryForObject(
            "INSERT INTO users (google_sub, email, display_name, role)"
                + " VALUES ('sc-edited-"
                + System.nanoTime()
                + "', 'edited@sc.test', 'Edited', 'player') RETURNING id",
            Long.class);

    Long q1 =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points) VALUES (1, ?, 7) RETURNING id",
            Long.class,
            u1);
    Long q2 =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points) VALUES (1, ?, 0) RETURNING id",
            Long.class,
            u2);
    Long q3 =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points) VALUES (1, ?, 4) RETURNING id",
            Long.class,
            u3);

    // Bet 1: clean pre-kickoff. bet 2-1 vs actual 2-1 → live recompute = 7. bet.points = 7.
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, points,"
            + " created_at, updated_at)"
            + " VALUES (?, ?, 2, 1, 7,"
            + " NOW() - INTERVAL '4 hours', NOW() - INTERVAL '4 hours')",
        q1,
        matchId);

    // Bet 2: placed-after kickoff. bet 2-1 vs actual 2-1 → live recompute = 7.
    // bet.points = 0 (was not credited because created after kickoff).
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, points,"
            + " created_at, updated_at)"
            + " VALUES (?, ?, 2, 1, 0,"
            + " NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour')",
        q2,
        matchId);

    // Bet 3: edited-after kickoff. Created 4h ago (pre-kickoff), updated 1h ago (post-kickoff).
    // Current prediction: 3-0 vs actual 2-1 → live recompute = outcome(3) only = 3.
    // bet.points = 4 (the original frozen score from before the edit).
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, points,"
            + " created_at, updated_at)"
            + " VALUES (?, ?, 3, 0, 4,"
            + " NOW() - INTERVAL '4 hours', NOW() - INTERVAL '1 hour')",
        q3,
        matchId);

    String token = jwt.issue(viewer);

    // ── Assert player 1: clean bet → frozen=7, note=null ─────────────────────
    mockMvc
        .perform(
            get("/api/ranking/" + u1 + "/scorecard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPoints").value(7))
        // The match for our seeded matchId is the first/only match in the GROUP stage for this
        // user.
        // We match by matchId using filter expression.
        .andExpect(
            jsonPath(
                    "$.stages[?(@.roundCode == 'GROUP')].matches[?(@.matchId == "
                        + matchId
                        + ")].points")
                .value(7))
        .andExpect(
            jsonPath(
                "$.stages[?(@.roundCode == 'GROUP')].matches[?(@.matchId == " + matchId + ")].note",
                hasItem(nullValue())));

    // ── Assert player 2: placed-after → frozen=0, note="PLACED_AFTER_KICKOFF" ─
    mockMvc
        .perform(
            get("/api/ranking/" + u2 + "/scorecard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPoints").value(0))
        .andExpect(
            jsonPath(
                    "$.stages[?(@.roundCode == 'GROUP')].matches[?(@.matchId == "
                        + matchId
                        + ")].points")
                .value(0))
        .andExpect(
            jsonPath(
                    "$.stages[?(@.roundCode == 'GROUP')].matches[?(@.matchId == "
                        + matchId
                        + ")].note")
                .value("PLACED_AFTER_KICKOFF"));

    // ── Assert player 3: edited-after → frozen=4, note="EDITED_AFTER_KICKOFF" ─
    mockMvc
        .perform(
            get("/api/ranking/" + u3 + "/scorecard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalPoints").value(4))
        .andExpect(
            jsonPath(
                    "$.stages[?(@.roundCode == 'GROUP')].matches[?(@.matchId == "
                        + matchId
                        + ")].points")
                .value(4))
        .andExpect(
            jsonPath(
                    "$.stages[?(@.roundCode == 'GROUP')].matches[?(@.matchId == "
                        + matchId
                        + ")].note")
                .value("EDITED_AFTER_KICKOFF"));

    // ── Assert per-round subtotal equals quiniela.points for player 3 ─────────
    mockMvc
        .perform(
            get("/api/ranking/" + u3 + "/scorecard").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stages[?(@.roundCode == 'GROUP')].points").value(4));

    // ── Cleanup: remove the inserted match (match is not in the auto-cleaned tables) ─
    jdbc.update("DELETE FROM match WHERE id = ?", matchId);
  }
}
