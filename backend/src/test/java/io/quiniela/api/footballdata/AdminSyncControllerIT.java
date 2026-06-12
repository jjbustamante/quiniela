package io.quiniela.api.footballdata;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

class AdminSyncControllerIT extends AbstractIntegrationTest {

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
        .perform(post("/api/admin/sync/daily").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanTriggerDaily() throws Exception {
    String token = tokenFor(UserRole.ADMIN, "g-admin");
    mockMvc
        .perform(post("/api/admin/sync/daily").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }

  @Test
  void adminCanTriggerSingleMatch() throws Exception {
    String token = tokenFor(UserRole.ADMIN, "g-admin2");
    mockMvc
        .perform(post("/api/admin/sync/match/424242").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk());
  }
}
