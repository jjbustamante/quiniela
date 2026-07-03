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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class BracketControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void getMeBracketReturnsAllGroupsAndKnockouts() throws Exception {
    var u = new User("g-br1", "br1@example.com", "BR1", null, UserRole.CAPTAIN);
    u.setInvitePath("br1-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(get("/api/bracket/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalMatches").value(104))
        .andExpect(jsonPath("$.totalBets").value(0))
        .andExpect(jsonPath("$.groups.length()").value(12))
        .andExpect(jsonPath("$.groups[0].code").value("A"))
        .andExpect(jsonPath("$.groups[0].matches.length()").value(6));
  }

  @Test
  void getMeBracketRequiresAuth() throws Exception {
    mockMvc.perform(get("/api/bracket/me")).andExpect(status().isUnauthorized());
  }

  @Autowired javax.sql.DataSource dataSource;

  @Test
  void saveBetUpsertsAndReturns200() throws Exception {
    var u = new User("g-br2", "br2@example.com", "BR2", null, UserRole.CAPTAIN);
    u.setInvitePath("br2-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    // Group-stage match id=1 (first seeded).
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/bracket/bet")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"matchId\":1,\"scoreT1\":2,\"scoreT2\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchId").value(1))
        .andExpect(jsonPath("$.scoreT1").value(2))
        .andExpect(jsonPath("$.scoreT2").value(1));

    // Second POST overwrites.
    mockMvc
        .perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                    "/api/bracket/bet")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"matchId\":1,\"scoreT1\":3,\"scoreT2\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scoreT1").value(3));
  }

  @Test
  void saveBetRejectsAfterGroupStageDeadline() throws Exception {
    org.springframework.jdbc.core.JdbcTemplate jdbc =
        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    try {
      jdbc.update(
          "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");

      var u = new User("g-br3", "br3@example.com", "BR3", null, UserRole.CAPTAIN);
      u.setInvitePath("br3-abc");
      u = users.save(u);
      String token = jwt.issue(u);

      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/bracket/bet")
                  .header("Authorization", "Bearer " + token)
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content("{\"matchId\":1,\"scoreT1\":2,\"scoreT2\":1}"))
          .andExpect(status().isLocked());
    } finally {
      // Restore an OPEN window (NOW()-relative). An absolute date here becomes a
      // past deadline once the calendar passes it, which leaks a closed window into
      // later tests via the shared singleton container.
      jdbc.update(
          "UPDATE tournament SET group_stage_deadline = NOW() + INTERVAL '30 days' WHERE id = 1");
    }
  }

  @Test
  void saveBetRejectsWhenMatchKickoffIsInThePast() throws Exception {
    org.springframework.jdbc.core.JdbcTemplate jdbc =
        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    // Keep round deadline open; push only match 1's kickoff into the past.
    try {
      jdbc.update("UPDATE match SET kickoff_at = NOW() - INTERVAL '1 hour' WHERE id = 1");

      var u = new User("g-br4", "br4@example.com", "BR4", null, UserRole.CAPTAIN);
      u.setInvitePath("br4-abc");
      u = users.save(u);
      String token = jwt.issue(u);

      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/bracket/bet")
                  .header("Authorization", "Bearer " + token)
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content("{\"matchId\":1,\"scoreT1\":2,\"scoreT2\":1}"))
          .andExpect(status().isLocked());
    } finally {
      // Restore match 1 kickoff to a future time so it doesn't pollute other tests.
      jdbc.update("UPDATE match SET kickoff_at = NOW() + INTERVAL '30 days' WHERE id = 1");
    }
  }

  @Test
  void saveBetSucceedsWhenMatchKickoffIsInTheFuture() throws Exception {
    org.springframework.jdbc.core.JdbcTemplate jdbc =
        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    // Ensure match 2 has a future kickoff and round deadline is open.
    try {
      jdbc.update("UPDATE match SET kickoff_at = NOW() + INTERVAL '2 hours' WHERE id = 2");

      var u = new User("g-br5", "br5@example.com", "BR5", null, UserRole.CAPTAIN);
      u.setInvitePath("br5-abc");
      u = users.save(u);
      String token = jwt.issue(u);

      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/bracket/bet")
                  .header("Authorization", "Bearer " + token)
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content("{\"matchId\":2,\"scoreT1\":1,\"scoreT2\":0}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.matchId").value(2));
    } finally {
      jdbc.update("UPDATE match SET kickoff_at = NOW() + INTERVAL '30 days' WHERE id = 2");
    }
  }

  /**
   * R16 fixture ids in the test seed (V007__seed_test_fixtures.sql) are 89-96 (round_id = 3). Every
   * knockout match starts with team_1_id/team_2_id NULL, matching production before
   * football-data.org sync assigns teams.
   */
  @Test
  void saveBetRejectsForKnockoutRoundWithNoTeamAssignedYet() throws Exception {
    org.springframework.jdbc.core.JdbcTemplate jdbc =
        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    try {
      // No team assigned to any R16 match; round's own first kickoff is still in the future.
      jdbc.update(
          "UPDATE match SET team_1_id = NULL, team_2_id = NULL, "
              + "kickoff_at = NOW() + INTERVAL '2 days' WHERE round_id = 3");

      var u = new User("g-br6", "br6@example.com", "BR6", null, UserRole.CAPTAIN);
      u.setInvitePath("br6-abc");
      u = users.save(u);
      String token = jwt.issue(u);

      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/bracket/bet")
                  .header("Authorization", "Bearer " + token)
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content("{\"matchId\":89,\"scoreT1\":1,\"scoreT2\":0}"))
          .andExpect(status().isLocked());
    } finally {
      jdbc.update(
          "UPDATE match SET team_1_id = NULL, team_2_id = NULL, "
              + "kickoff_at = NOW() + INTERVAL '30 days' WHERE round_id = 3");
    }
  }

  @Test
  void saveBetSucceedsForKnockoutRoundOnceATeamIsKnownAndBeforeItsFirstKickoff() throws Exception {
    org.springframework.jdbc.core.JdbcTemplate jdbc =
        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    try {
      // Whole round still ahead of its own first kickoff; one match has a team assigned
      // (mirrors production: football-data.org has resolved this R16 slot's pairing).
      jdbc.update("UPDATE match SET kickoff_at = NOW() + INTERVAL '2 days' WHERE round_id = 3");
      jdbc.update(
          "UPDATE match SET team_1_id = (SELECT id FROM team ORDER BY id LIMIT 1) WHERE id = 89");

      var u = new User("g-br7", "br7@example.com", "BR7", null, UserRole.CAPTAIN);
      u.setInvitePath("br7-abc");
      u = users.save(u);
      String token = jwt.issue(u);

      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/bracket/bet")
                  .header("Authorization", "Bearer " + token)
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content("{\"matchId\":89,\"scoreT1\":1,\"scoreT2\":0}"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.matchId").value(89));
    } finally {
      jdbc.update(
          "UPDATE match SET team_1_id = NULL, team_2_id = NULL, "
              + "kickoff_at = NOW() + INTERVAL '30 days' WHERE round_id = 3");
    }
  }

  @Test
  void saveBetRejectsForKnockoutRoundAfterItsFirstKickoffEvenForAnotherStillFutureMatch()
      throws Exception {
    org.springframework.jdbc.core.JdbcTemplate jdbc =
        new org.springframework.jdbc.core.JdbcTemplate(dataSource);
    try {
      // Match 89 is the round's own first kickoff and has already started; match 90 is a
      // DIFFERENT match in the same round, still in the future by itself. The round-wide
      // window should already be closed for the whole round, not just match 89.
      jdbc.update(
          "UPDATE match SET kickoff_at = NOW() + INTERVAL '5 days' WHERE round_id = 3 "
              + "AND id NOT IN (89, 90)");
      jdbc.update(
          "UPDATE match SET team_1_id = (SELECT id FROM team ORDER BY id LIMIT 1), "
              + "kickoff_at = NOW() - INTERVAL '1 hour' WHERE id = 89");
      jdbc.update("UPDATE match SET kickoff_at = NOW() + INTERVAL '2 hours' WHERE id = 90");

      var u = new User("g-br8", "br8@example.com", "BR8", null, UserRole.CAPTAIN);
      u.setInvitePath("br8-abc");
      u = users.save(u);
      String token = jwt.issue(u);

      mockMvc
          .perform(
              org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(
                      "/api/bracket/bet")
                  .header("Authorization", "Bearer " + token)
                  .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                  .content("{\"matchId\":90,\"scoreT1\":1,\"scoreT2\":0}"))
          .andExpect(status().isLocked());
    } finally {
      jdbc.update(
          "UPDATE match SET team_1_id = NULL, team_2_id = NULL, "
              + "kickoff_at = NOW() + INTERVAL '30 days' WHERE round_id = 3");
    }
  }
}
