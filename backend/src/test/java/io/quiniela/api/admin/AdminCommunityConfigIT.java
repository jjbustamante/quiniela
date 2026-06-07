package io.quiniela.api.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AdminCommunityConfigIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  private User saveUser(String slug, String name, UserRole role) {
    var u = new User("g-" + slug, slug + "@example.com", name, null, role);
    u.setInvitePath(slug);
    return users.save(u);
  }

  @Test
  void adminReadsAndUpdatesCommunityConfig() throws Exception {
    var admin = saveUser("cc-admin", "CC Admin", UserRole.ADMIN);
    String token = jwt.issue(admin);

    // enable with a valid WhatsApp url
    mockMvc
        .perform(
            put("/api/admin/community-config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://chat.whatsapp.com/ABC\",\"enabled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value("https://chat.whatsapp.com/ABC"))
        .andExpect(jsonPath("$.enabled").value(true));

    // enabling with a non-whatsapp url is rejected
    mockMvc
        .perform(
            put("/api/admin/community-config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"url\":\"https://evil.example/x\",\"enabled\":true}"))
        .andExpect(status().isBadRequest());

    // a player is forbidden
    var player = saveUser("cc-player", "CC Player", UserRole.PLAYER);
    mockMvc
        .perform(
            get("/api/admin/community-config")
                .header("Authorization", "Bearer " + jwt.issue(player)))
        .andExpect(status().isForbidden());
  }
}
