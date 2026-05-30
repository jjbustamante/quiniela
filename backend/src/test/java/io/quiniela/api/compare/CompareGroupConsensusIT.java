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
  }

  private String userWithBetOnMatch1(String slug, int t1, int t2) {
    var u =
        new User("g-" + slug, slug + "@example.com", slug.toUpperCase(), null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    u = users.save(u);
    jdbc.update("INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?)", u.getId());
    Long qid =
        jdbc.queryForObject("SELECT id FROM quiniela WHERE user_id = ?", Long.class, u.getId());
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2) VALUES (?,1,?,?)",
        qid,
        t1,
        t2);
    return jwt.issue(u);
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
        .andExpect(jsonPath("$.matches[0].matchId").value(1))
        .andExpect(jsonPath("$.matches[0].revealed").value(false))
        .andExpect(jsonPath("$.matches[0].distribution.length()").value(0))
        .andExpect(jsonPath("$.matches[0].myScoreT1").value(2))
        .andExpect(jsonPath("$.matches[0].myScoreT2").value(1));
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
        .andExpect(jsonPath("$.matches[0].revealed").value(true))
        .andExpect(jsonPath("$.matches[0].totalPicks").value(3))
        .andExpect(jsonPath("$.matches[0].majority").value(true))
        .andExpect(jsonPath("$.matches[0].rebel").value(false));
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
        .andExpect(jsonPath("$.matches[0].majority").value(false))
        .andExpect(jsonPath("$.matches[0].rebel").value(true));
  }
}
