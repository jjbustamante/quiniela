package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.auth.JwtService;
import io.quiniela.api.bet.BetRepository;
import io.quiniela.api.quiniela.Quiniela;
import io.quiniela.api.quiniela.QuinielaRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class PaulControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired JwtService jwt;
  @Autowired DataSource dataSource;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void suggestReturnsScoreAndReasoning() throws Exception {
    var u = new User("g-paul1", "p1@example.com", "P1", null, UserRole.PLAYER);
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(post("/api/paul/suggest?matchId=1").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scoreT1").isNumber())
        .andExpect(jsonPath("$.scoreT2").isNumber())
        .andExpect(jsonPath("$.reasoning").isString());
  }

  @Test
  void fillAllPopulatesEveryUnsetGroupBet() throws Exception {
    var u = new User("g-paul2", "p2@example.com", "P2", null, UserRole.PLAYER);
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(post("/api/paul/fill").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.created").value(72)); // all 72 group matches

    Quiniela q = quinielas.findByPoolIdAndUserId(1L, u.getId()).orElseThrow();
    assertThat(bets.findByQuinielaId(q.getId())).hasSize(72);
  }

  @Test
  void fillAllRejectedWhenGroupStageLocked() throws Exception {
    var u = new User("g-paul3", "p3@example.com", "P3", null, UserRole.PLAYER);
    u = users.save(u);
    String token = jwt.issue(u);

    // Move the group-stage deadline into the past — the round is now closed.
    new JdbcTemplate(dataSource)
        .update(
            "UPDATE tournament SET group_stage_deadline = NOW() - INTERVAL '1 hour' WHERE id = 1");

    mockMvc
        .perform(post("/api/paul/fill").header("Authorization", "Bearer " + token))
        .andExpect(status().isLocked());

    // No bets should have been written (transaction rolled back on the first locked save).
    Quiniela q = quinielas.findByPoolIdAndUserId(1L, u.getId()).orElse(null);
    if (q != null) {
      assertThat(bets.findByQuinielaId(q.getId())).isEmpty();
    }
  }
}
