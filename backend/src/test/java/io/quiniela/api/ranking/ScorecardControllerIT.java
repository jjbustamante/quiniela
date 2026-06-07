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
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2) VALUES (?,1,2,1)", qid);
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
}
