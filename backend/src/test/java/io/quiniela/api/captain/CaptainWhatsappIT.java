package io.quiniela.api.captain;

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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class CaptainWhatsappIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired JdbcTemplate jdbc;

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
  void captainSeesOwnPlayersAndTogglesThem() throws Exception {
    var cap = saveUser("cw-cap", "Cap", UserRole.CAPTAIN);
    var mine = saveUser("cw-mine", "Mine", UserRole.PLAYER);
    var theirs = saveUser("cw-theirs", "Theirs", UserRole.PLAYER);
    jdbc.update("UPDATE users SET invited_by_user_id = ? WHERE id = ?", cap.getId(), mine.getId());
    // theirs invited by someone else
    var other = saveUser("cw-other", "Other", UserRole.CAPTAIN);
    jdbc.update(
        "UPDATE users SET invited_by_user_id = ? WHERE id = ?", other.getId(), theirs.getId());

    String token = jwt.issue(cap);

    // roster shows only my player
    mockMvc
        .perform(get("/api/captain/whatsapp-roster").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$..userId").value(org.hamcrest.Matchers.contains(mine.getId().intValue())));

    // toggling my player on succeeds
    mockMvc
        .perform(
            put("/api/captain/whatsapp-visibility")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + mine.getId() + ",\"visible\":true}"))
        .andExpect(status().isOk());
    Boolean v =
        jdbc.queryForObject(
            "SELECT whatsapp_group_visible FROM users WHERE id = ?", Boolean.class, mine.getId());
    Assertions.assertTrue(v);

    // toggling someone else's player is forbidden
    mockMvc
        .perform(
            put("/api/captain/whatsapp-visibility")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":" + theirs.getId() + ",\"visible\":true}"))
        .andExpect(status().isForbidden());

    // a player calling the roster is forbidden
    mockMvc
        .perform(
            get("/api/captain/whatsapp-roster")
                .header("Authorization", "Bearer " + jwt.issue(mine)))
        .andExpect(status().isForbidden());
  }
}
