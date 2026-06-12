package io.quiniela.api.ranking;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class RankingControllerIT extends AbstractIntegrationTest {

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

  @Test
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/ranking")).andExpect(status().isUnauthorized());
  }

  @Test
  void emptyPoolReturnsNoEntriesNot500() throws Exception {
    var caller = saveUser("rk-empty", "Empty Caller");
    String token = jwt.issue(caller);

    mockMvc
        .perform(get("/api/ranking").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(0))
        .andExpect(jsonPath("$.updatedAt").isString());
  }

  @Test
  void tiesShareRankAndCallerFlagsAsYou() throws Exception {
    // 3 players in pool 1: Ana (10), Beto (10), Carmen (5).
    // Expected ranks: Ana = 1, Beto = 1, Carmen = 3 (RANK semantics).
    // Sort within tie: Ana before Beto (display_name ASC).
    var ana = saveUser("rk-ana", "Ana");
    var beto = saveUser("rk-beto", "Beto");
    var carmen = saveUser("rk-carmen", "Carmen");
    insertQuiniela(ana.getId(), 10);
    insertQuiniela(beto.getId(), 10);
    insertQuiniela(carmen.getId(), 5);

    String betoToken = jwt.issue(beto);

    mockMvc
        .perform(get("/api/ranking").header("Authorization", "Bearer " + betoToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entries.length()").value(3))
        // Ana first (alpha tiebreak), rank 1, not the caller.
        .andExpect(jsonPath("$.entries[0].displayName").value("Ana"))
        .andExpect(jsonPath("$.entries[0].rank").value(1))
        .andExpect(jsonPath("$.entries[0].points").value(10))
        .andExpect(jsonPath("$.entries[0].isYou").value(false))
        .andExpect(jsonPath("$.entries[0].delta").doesNotExist())
        // Beto second, also rank 1 (tied), is the caller.
        .andExpect(jsonPath("$.entries[1].displayName").value("Beto"))
        .andExpect(jsonPath("$.entries[1].rank").value(1))
        .andExpect(jsonPath("$.entries[1].isYou").value(true))
        // Carmen third, rank 3 (RANK skips 2 on ties).
        .andExpect(jsonPath("$.entries[2].displayName").value("Carmen"))
        .andExpect(jsonPath("$.entries[2].rank").value(3))
        .andExpect(jsonPath("$.entries[2].isYou").value(false));
  }

  @Test
  void liveScoring_falseWhenNoLiveMatch() throws Exception {
    var caller = saveUser("rk-ls-false", "LS False Caller");
    String token = jwt.issue(caller);

    mockMvc
        .perform(get("/api/ranking").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.liveScoring").value(false));
  }

  @Test
  void liveScoring_trueWhenAMatchIsLive() throws Exception {
    var caller = saveUser("rk-ls-true", "LS True Caller");
    String token = jwt.issue(caller);

    // Seed match 1 as live: score present, played=FALSE.
    jdbc.update("UPDATE match SET score_t1 = 1, score_t2 = 0, played = FALSE WHERE id = 1");

    try {
      mockMvc
          .perform(get("/api/ranking").header("Authorization", "Bearer " + token))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.liveScoring").value(true));
    } finally {
      jdbc.update("UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE WHERE id = 1");
    }
  }

  private User saveUser(String slug, String displayName) {
    var u = new User("g-" + slug, slug + "@example.com", displayName, null, UserRole.CAPTAIN);
    u.setInvitePath(slug);
    return users.save(u);
  }

  private void insertQuiniela(Long userId, int points) {
    jdbc.update(
        "INSERT INTO quiniela (pool_id, user_id, points, created_at, updated_at)"
            + " VALUES (?, ?, ?, NOW(), NOW())",
        1L,
        userId,
        points);
  }
}
