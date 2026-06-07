package io.quiniela.api.me;

import static org.hamcrest.Matchers.nullValue;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class MeControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired JdbcTemplate jdbc;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void returnsCurrentUser() throws Exception {
    var u = new User("g-1", "me@example.com", "Me", null, UserRole.CAPTAIN);
    u.setInvitePath("me-abc123");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(u.getId()))
        .andExpect(jsonPath("$.role").value("CAPTAIN"))
        .andExpect(jsonPath("$.invitePath").value("me-abc123"))
        .andExpect(jsonPath("$.canInvite").value(true));
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void meIncludesTimezoneDefault() throws Exception {
    var u = new User("g-tz1", "tz1@example.com", "Tz One", null, UserRole.PLAYER);
    u.setInvitePath("tz1-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("America/Bogota"));
  }

  @Test
  void putTimezonePersistsValidZone() throws Exception {
    var u = new User("g-tz2", "tz2@example.com", "Tz Two", null, UserRole.PLAYER);
    u.setInvitePath("tz2-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(
            put("/api/me/timezone")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"America/Caracas\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.timezone").value("America/Caracas"));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + token))
        .andExpect(jsonPath("$.timezone").value("America/Caracas"));
  }

  @Test
  void putTimezoneRejectsInvalidZone() throws Exception {
    var u = new User("g-tz3", "tz3@example.com", "Tz Three", null, UserRole.PLAYER);
    u.setInvitePath("tz3-abc");
    u = users.save(u);
    String token = jwt.issue(u);

    mockMvc
        .perform(
            put("/api/me/timezone")
                .header("Authorization", "Bearer " + token)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"Mars/Phobos\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void putTimezoneUnauthenticatedReturns401() throws Exception {
    mockMvc
        .perform(
            put("/api/me/timezone")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"timezone\":\"UTC\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void meExposesWhatsappUrlOnlyToEligibleViewers() throws Exception {
    // Enable the WhatsApp group on pool id=1
    jdbc.execute(
        "UPDATE pool SET whatsapp_group_url='https://chat.whatsapp.com/ABC',"
            + " whatsapp_group_enabled=true WHERE id=1");

    // Admin — always sees the url
    var admin =
        users.save(new User("g-wa-admin", "admin@wa.com", "Admin WA", null, UserRole.ADMIN));
    String adminToken = jwt.issue(admin);

    // Captain — always sees the url
    var captain =
        users.save(
            new User("g-wa-captain", "captain@wa.com", "Captain WA", null, UserRole.CAPTAIN));
    String captainToken = jwt.issue(captain);

    // Player with whatsapp_group_visible=true
    var visiblePlayer =
        users.save(new User("g-wa-vis", "vis@wa.com", "Visible Player", null, UserRole.PLAYER));
    jdbc.update("UPDATE users SET whatsapp_group_visible=true WHERE id=?", visiblePlayer.getId());
    String visibleToken = jwt.issue(visiblePlayer);

    // Player with whatsapp_group_visible=false (default)
    var hiddenPlayer =
        users.save(new User("g-wa-hid", "hid@wa.com", "Hidden Player", null, UserRole.PLAYER));
    String hiddenToken = jwt.issue(hiddenPlayer);

    String url = "https://chat.whatsapp.com/ABC";

    // All eligible viewers see the url
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.whatsappGroupUrl").value(url));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + captainToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.whatsappGroupUrl").value(url));

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + visibleToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.whatsappGroupUrl").value(url));

    // Hidden player sees null
    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + hiddenToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.whatsappGroupUrl").value(nullValue()));

    // Disable the master switch — even admin sees null
    jdbc.execute("UPDATE pool SET whatsapp_group_enabled=false WHERE id=1");

    mockMvc
        .perform(get("/api/me").header("Authorization", "Bearer " + adminToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.whatsappGroupUrl").value(nullValue()));
  }
}
