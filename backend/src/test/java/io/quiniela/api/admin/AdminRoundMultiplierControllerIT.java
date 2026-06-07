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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class AdminRoundMultiplierControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  User admin;
  User captain;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    admin = saveUser("rm-adm", "RM Admin", UserRole.ADMIN);
    captain = saveUser("rm-cap", "RM Captain", UserRole.CAPTAIN);
  }

  // `round` is seeded reference data not reset by cleanWritableTables; update tests mutate it.
  @AfterEach
  void restoreSeededMultipliers() {
    new JdbcTemplate(dataSource)
        .update("UPDATE round SET points_multiplier = CASE WHEN code = 'GROUP' THEN 1 ELSE 2 END");
  }

  private User saveUser(String slug, String name, UserRole role) {
    var u = new User("g-" + slug, slug + "@example.com", name, null, role);
    u.setInvitePath(slug);
    return users.save(u);
  }

  @Test
  void getRequiresAuth() throws Exception {
    mockMvc.perform(get("/api/admin/round-multipliers")).andExpect(status().isUnauthorized());
  }

  @Test
  void getRequiresAdmin() throws Exception {
    String token = jwt.issue(captain);
    mockMvc
        .perform(get("/api/admin/round-multipliers").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void getReturnsKnockoutRoundsOnly() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(get("/api/admin/round-multipliers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds.length()").value(6))
        .andExpect(jsonPath("$.rounds[0].code").value("R32"))
        .andExpect(jsonPath("$.rounds[0].multiplier").value(2));
  }

  @Test
  void updateRequiresAdmin() throws Exception {
    String token = jwt.issue(captain);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rounds\":[{\"code\":\"R32\",\"multiplier\":3}]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminUpdatesMultipliers() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"rounds\":[{\"code\":\"R16\",\"multiplier\":3},"
                        + "{\"code\":\"FINAL\",\"multiplier\":5}]}"))
        .andExpect(status().isOk());

    mockMvc
        .perform(get("/api/admin/round-multipliers").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.rounds[?(@.code == 'R16')].multiplier").value(3))
        .andExpect(jsonPath("$.rounds[?(@.code == 'FINAL')].multiplier").value(5))
        .andExpect(jsonPath("$.rounds[?(@.code == 'R32')].multiplier").value(2));
  }

  @Test
  void rejectsMultiplierBelowOne() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rounds\":[{\"code\":\"R32\",\"multiplier\":0}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsEditingTheGroupStage() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rounds\":[{\"code\":\"GROUP\",\"multiplier\":2}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsUnknownRoundCode() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/round-multipliers")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rounds\":[{\"code\":\"BOGUS\",\"multiplier\":2}]}"))
        .andExpect(status().isBadRequest());
  }
}
