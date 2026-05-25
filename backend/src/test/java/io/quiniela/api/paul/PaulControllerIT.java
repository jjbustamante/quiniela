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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class PaulControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired QuinielaRepository quinielas;
  @Autowired BetRepository bets;
  @Autowired JwtService jwt;

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
}
