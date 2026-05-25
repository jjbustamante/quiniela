package io.quiniela.api.invite;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class InviteControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void resolvesValidPath() throws Exception {
    var captain = new User("g-cap", "cap@example.com", "Captain Marvel", null, UserRole.CAPTAIN);
    captain.setInvitePath("captain-marvel-q1w2e3");
    users.save(captain);

    mockMvc
        .perform(get("/api/invite/captain-marvel-q1w2e3"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valid").value(true))
        .andExpect(jsonPath("$.inviterDisplayName").value("Captain Marvel"))
        .andExpect(jsonPath("$.inviterRole").value("CAPTAIN"));
  }

  @Test
  void unknownPathReturns404() throws Exception {
    mockMvc.perform(get("/api/invite/does-not-exist-xxx")).andExpect(status().isNotFound());
  }
}
