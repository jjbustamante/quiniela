package io.quiniela.api.bracket;

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

class BracketLockUiIT extends AbstractIntegrationTest {

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
    // V006 baseline values — restore so other ITs don't observe a leaked state.
    jdbc.update(
        "UPDATE tournament SET "
            + " group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC',"
            + " knockout_deadline    = TIMESTAMPTZ '2026-06-28 17:00 UTC'"
            + " WHERE id = 1");
    // Remove any team seeded into R32 by lock tests, and restore R32 kickoffs to the
    // future so a round-locked test doesn't leak a closed window into later tests.
    jdbc.update(
        "UPDATE match SET team_1_id = NULL, kickoff_at = NOW() + INTERVAL '30 days' "
            + "WHERE round_id = (SELECT id FROM round WHERE tournament_id = 1 AND code = 'R32' LIMIT 1)");
  }

  /**
   * Assigns one team to the first R32 match so the per-round unlocked check resolves to true
   * (groupLocked AND teams present).
   */
  private void seedOneR32Team() {
    jdbc.update(
        "UPDATE match SET team_1_id = (SELECT id FROM team ORDER BY id LIMIT 1) "
            + "WHERE id = (SELECT m.id FROM match m "
            + "  JOIN round r ON r.id = m.round_id "
            + "  WHERE r.tournament_id = 1 AND r.code = 'R32' "
            + "  ORDER BY m.id LIMIT 1)");
  }

  @Test
  void bracketReportsUnlockedBeforeDeadlines() throws Exception {
    jdbc.update(
        "UPDATE tournament SET "
            + " group_stage_deadline = NOW() + INTERVAL '7 days',"
            + " knockout_deadline    = NOW() + INTERVAL '30 days'"
            + " WHERE id = 1");

    String token = issueTokenFor("lock-ui-before");

    mockMvc
        .perform(get("/api/bracket/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[0].locked").value(false))
        .andExpect(jsonPath("$.knockouts[0].locked").value(false))
        .andExpect(jsonPath("$.knockouts[0].unlocked").value(false))
        .andExpect(jsonPath("$.groupStageDeadline").isString())
        .andExpect(jsonPath("$.knockoutDeadline").isString());
  }

  @Test
  void bracketReportsGroupLockedAfterGroupDeadline() throws Exception {
    jdbc.update(
        "UPDATE tournament SET "
            + " group_stage_deadline = NOW() - INTERVAL '1 hour',"
            + " knockout_deadline    = NOW() + INTERVAL '30 days'"
            + " WHERE id = 1");
    seedOneR32Team();

    String token = issueTokenFor("lock-ui-groups-locked");

    mockMvc
        .perform(get("/api/bracket/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[0].locked").value(true))
        .andExpect(jsonPath("$.knockouts[0].locked").value(false))
        .andExpect(jsonPath("$.knockouts[0].unlocked").value(true));
  }

  @Test
  void bracketReportsKnockoutRoundLockedAfterItsOwnFirstKickoff() throws Exception {
    // The shared tournament.knockout_deadline is no longer what locks a knockout round —
    // each round locks at its OWN first kickoff. Push R32's own matches into the past.
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '10 days' WHERE id = 1");
    seedOneR32Team();
    jdbc.update(
        "UPDATE match SET kickoff_at = NOW() - INTERVAL '1 hour' WHERE round_id = "
            + "(SELECT id FROM round WHERE tournament_id = 1 AND code = 'R32' LIMIT 1)");

    String token = issueTokenFor("lock-ui-knockouts-locked");

    mockMvc
        .perform(get("/api/bracket/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.groups[0].locked").value(true))
        .andExpect(jsonPath("$.knockouts[0].locked").value(true))
        .andExpect(jsonPath("$.knockouts[0].unlocked").value(true))
        .andExpect(jsonPath("$.knockouts[0].deadline").isString());
  }

  @Test
  void bracketReportsKnockoutRoundDeadlineAsItsOwnFirstKickoffNotTheSharedOne() throws Exception {
    // R32's own first kickoff is what deadline reports — not the (now unused) shared
    // tournament.knockout_deadline column, even when the two differ wildly.
    jdbc.update(
        "UPDATE tournament SET "
            + " group_stage_deadline = NOW() - INTERVAL '10 days',"
            + " knockout_deadline    = NOW() - INTERVAL '365 days'"
            + " WHERE id = 1");
    seedOneR32Team();
    java.time.Instant future =
        java.time.Instant.now()
            .plus(java.time.Duration.ofDays(3))
            .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    jdbc.update(
        "UPDATE match SET kickoff_at = ? WHERE round_id = "
            + "(SELECT id FROM round WHERE tournament_id = 1 AND code = 'R32' LIMIT 1) "
            + "AND id = (SELECT id FROM match WHERE round_id = "
            + "(SELECT id FROM round WHERE tournament_id = 1 AND code = 'R32' LIMIT 1) ORDER BY id LIMIT 1)",
        java.sql.Timestamp.from(future));

    String token = issueTokenFor("lock-ui-round-deadline");

    mockMvc
        .perform(get("/api/bracket/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.knockouts[0].locked").value(false))
        .andExpect(jsonPath("$.knockouts[0].deadline").value(future.toString()));
  }

  private String issueTokenFor(String slug) {
    var u =
        new User("g-" + slug, slug + "@example.com", slug.toUpperCase(), null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    u = users.save(u);
    return jwt.issue(u);
  }
}
