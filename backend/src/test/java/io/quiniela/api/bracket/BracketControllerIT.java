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
      jdbc.update(
          "UPDATE tournament SET group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC' WHERE id = 1");
    }
  }
}
