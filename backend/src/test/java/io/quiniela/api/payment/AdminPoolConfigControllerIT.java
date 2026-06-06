package io.quiniela.api.payment;

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

class AdminPoolConfigControllerIT extends AbstractIntegrationTest {

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
    admin = saveUser("cfg-adm", "Cfg Admin", UserRole.ADMIN);
    captain = saveUser("cfg-cap", "Cfg Captain", UserRole.CAPTAIN);
  }

  // pool + prize_split are seeded reference data NOT reset by cleanWritableTables; the update
  // tests mutate the singleton pool 1, so restore the seeded config to avoid leaking to other ITs.
  @AfterEach
  void restoreSeededConfig() {
    var jdbc = new JdbcTemplate(dataSource);
    jdbc.update("UPDATE pool SET entry_fee_cents = 2000, house_cut_percentage = 0 WHERE id = 1");
    jdbc.update("DELETE FROM prize_split WHERE pool_id = 1");
    jdbc.update(
        "INSERT INTO prize_split (pool_id, rank, percentage) VALUES (1,1,80),(1,2,15),(1,3,5)");
  }

  private User saveUser(String slug, String name, UserRole role) {
    var u = new User("g-" + slug, slug + "@example.com", name, null, role);
    u.setInvitePath(slug);
    return users.save(u);
  }

  @Test
  void getRequiresAuth() throws Exception {
    mockMvc.perform(get("/api/admin/pool-config")).andExpect(status().isUnauthorized());
  }

  @Test
  void getRequiresAdmin() throws Exception {
    String token = jwt.issue(captain);
    mockMvc
        .perform(get("/api/admin/pool-config").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void getReturnsSeededConfig() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(get("/api/admin/pool-config").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entryFeeCents").value(2000))
        .andExpect(jsonPath("$.houseCutPercentage").value(0))
        .andExpect(jsonPath("$.prizeSplit.length()").value(3))
        .andExpect(jsonPath("$.prizeSplit[0].percentage").value(80));
  }

  @Test
  void updateRequiresAdmin() throws Exception {
    String token = jwt.issue(captain);
    mockMvc
        .perform(
            put("/api/admin/pool-config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entryFeeCents\":3000,\"houseCutPercentage\":10,"
                        + "\"prizeSplit\":[{\"rank\":1,\"percentage\":100}]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminUpdatesFeeHouseCutAndSplit() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/pool-config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entryFeeCents\":2500,\"houseCutPercentage\":10,"
                        + "\"prizeSplit\":[{\"rank\":1,\"percentage\":70},"
                        + "{\"rank\":2,\"percentage\":30}]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entryFeeCents").value(2500))
        .andExpect(jsonPath("$.houseCutPercentage").value(10))
        .andExpect(jsonPath("$.prizeSplit.length()").value(2));

    // Re-GET reflects the change (and the old rank-3 row is gone).
    mockMvc
        .perform(get("/api/admin/pool-config").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.entryFeeCents").value(2500))
        .andExpect(jsonPath("$.prizeSplit.length()").value(2));
  }

  @Test
  void rejectsPrizeSplitNotSummingTo100() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/pool-config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entryFeeCents\":2000,\"houseCutPercentage\":0,"
                        + "\"prizeSplit\":[{\"rank\":1,\"percentage\":50},"
                        + "{\"rank\":2,\"percentage\":40}]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void rejectsHouseCutOver100() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/pool-config")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"entryFeeCents\":2000,\"houseCutPercentage\":150,"
                        + "\"prizeSplit\":[{\"rank\":1,\"percentage\":100}]}"))
        .andExpect(status().isBadRequest());
  }
}
