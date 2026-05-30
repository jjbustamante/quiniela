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

class CompareH2HIT extends AbstractIntegrationTest {

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
  void restoreDeadlinesAndMatch() {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = TIMESTAMPTZ '2026-06-11 17:00 UTC',"
            + " knockout_deadline = TIMESTAMPTZ '2026-06-28 17:00 UTC' WHERE id = 1");
    // Reset match 1 result so a played-match test can't leak into later tests.
    jdbc.update("UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE WHERE id = 1");
  }

  private long createUserWithBetOnMatch1(String slug, int t1, int t2) {
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
    return u.getId();
  }

  private String token(long userId) {
    return jwt.issue(users.findById(userId).orElseThrow());
  }

  @Test
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/compare/h2h?vs=1")).andExpect(status().isUnauthorized());
  }

  @Test
  void hidesRivalPickBeforeLock() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() + INTERVAL '7 days' WHERE id = 1");
    long me = createUserWithBetOnMatch1("h2h-pre-me", 2, 1);
    long rival = createUserWithBetOnMatch1("h2h-pre-rival", 0, 0);

    mockMvc
        .perform(get("/api/compare/h2h?vs=" + rival).header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rivalUserId").value((int) rival))
        .andExpect(jsonPath("$.matches[0].matchId").value(1))
        .andExpect(jsonPath("$.matches[0].revealed").value(false))
        .andExpect(jsonPath("$.matches[0].state").value("hidden"))
        .andExpect(jsonPath("$.matches[0].myScoreT1").value(2))
        .andExpect(jsonPath("$.matches[0].rivalScoreT1").doesNotExist());
  }

  @Test
  void revealsRivalPickAndClassifiesDifferAfterLock() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    long me = createUserWithBetOnMatch1("h2h-diff-me", 2, 1);
    long rival = createUserWithBetOnMatch1("h2h-diff-rival", 0, 0);

    mockMvc
        .perform(get("/api/compare/h2h?vs=" + rival).header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.differCount").value(1))
        .andExpect(jsonPath("$.agreeCount").value(0))
        .andExpect(jsonPath("$.matches[0].revealed").value(true))
        .andExpect(jsonPath("$.matches[0].state").value("differ"))
        .andExpect(jsonPath("$.matches[0].rivalScoreT1").value(0))
        .andExpect(jsonPath("$.matches[0].rivalScoreT2").value(0));
  }

  @Test
  void returns400ForUnknownRival() throws Exception {
    long me = createUserWithBetOnMatch1("h2h-unk-me", 1, 0);
    mockMvc
        .perform(get("/api/compare/h2h?vs=999999").header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void classifiesAgreeWhenPicksMatch() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    long me = createUserWithBetOnMatch1("h2h-agree-me", 1, 1);
    long rival = createUserWithBetOnMatch1("h2h-agree-rival", 1, 1);

    mockMvc
        .perform(get("/api/compare/h2h?vs=" + rival).header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.agreeCount").value(1))
        .andExpect(jsonPath("$.differCount").value(0))
        .andExpect(jsonPath("$.matches[0].state").value("agree"));
  }

  @Test
  void tallySumsPointsOnPlayedMatches() throws Exception {
    jdbc.update(
        "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");
    long me = createUserWithBetOnMatch1("h2h-tally-me", 2, 1); // exact -> 5 pts
    long rival = createUserWithBetOnMatch1("h2h-tally-rival", 3, 1); // correct winner only -> 2 pts
    // Record the real result for match 1 as 2-1 and mark played (fires scoring trigger).
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = 1");

    mockMvc
        .perform(get("/api/compare/h2h?vs=" + rival).header("Authorization", "Bearer " + token(me)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.myPoints").value(5))
        .andExpect(jsonPath("$.rivalPoints").value(2));
  }
}
