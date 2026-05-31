package io.quiniela.api.paul;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import({FakePaulOracleConfig.class, SynchronousPaulExecutorConfig.class})
class PaulAdminControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  private String tokenFor(UserRole role, String sub) {
    var u = users.save(new User(sub, sub + "@example.com", sub, null, role));
    return jwt.issue(u);
  }

  @Test
  void nonAdminIsForbidden() throws Exception {
    String token = tokenFor(UserRole.PLAYER, "g-pl");
    mockMvc
        .perform(post("/api/admin/paul/generate").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminGenerateThenSynthesizeThenReveal() throws Exception {
    String token = tokenFor(UserRole.ADMIN, "g-admin");

    // Generate kicks off a background job (202). With the synchronous test executor it has
    // finished by the time the POST returns, so the immediate status poll reports DONE.
    mockMvc
        .perform(post("/api/admin/paul/generate").header("Authorization", "Bearer " + token))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.phase").value("generate"));

    mockMvc
        .perform(get("/api/admin/paul/status").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phase").value("generate"))
        .andExpect(jsonPath("$.state").value("DONE"))
        .andExpect(jsonPath("$.processed").value(144))
        .andExpect(jsonPath("$.total").value(144));

    mockMvc
        .perform(post("/api/admin/paul/synthesize").header("Authorization", "Bearer " + token))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.phase").value("synthesize"));

    mockMvc
        .perform(get("/api/admin/paul/status").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.phase").value("synthesize"))
        .andExpect(jsonPath("$.state").value("DONE"))
        .andExpect(jsonPath("$.processed").value(72));

    // Reveal stays synchronous (DB-only, no LLM calls).
    mockMvc
        .perform(post("/api/admin/paul/reveal").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.betsCreated").value(72));
  }
}
