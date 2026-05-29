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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

class PaymentControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  JdbcTemplate jdbc;

  // Users created per-test (all tests share pool 1).
  User captain;
  User captain2;
  User player1;
  User player2;
  User admin;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);

    // Create captain (the subgroup owner).
    captain = saveUser("pmt-captain", "Captain C", UserRole.CAPTAIN);
    addMember(captain.getId());

    // Create captain2 (a different captain who doesn't own P1/P2).
    captain2 = saveUser("pmt-captain2", "Captain C2", UserRole.CAPTAIN);
    addMember(captain2.getId());

    // Create player1 invited by captain.
    player1 = saveUser("pmt-player1", "Player P1", UserRole.PLAYER);
    player1.setInvitedByUserId(captain.getId());
    player1 = users.save(player1);
    addMember(player1.getId());

    // Create player2 invited by captain.
    player2 = saveUser("pmt-player2", "Player P2", UserRole.PLAYER);
    player2.setInvitedByUserId(captain.getId());
    player2 = users.save(player2);
    addMember(player2.getId());

    // Create admin.
    admin = saveUser("pmt-admin", "Admin A", UserRole.ADMIN);
    addMember(admin.getId());
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private User saveUser(String slug, String displayName, UserRole role) {
    var u = new User("g-" + slug, slug + "@example.com", displayName, null, role);
    u.setInvitePath(slug);
    return users.save(u);
  }

  private void addMember(Long userId) {
    jdbc.update(
        "INSERT INTO pool_membership (pool_id, user_id, joined_at)"
            + " VALUES (1, ?, NOW()) ON CONFLICT DO NOTHING",
        userId);
  }

  private void markPaidInDb(Long userId) {
    jdbc.update(
        "INSERT INTO payment (pool_id, user_id, paid, paid_at, marked_paid_by)"
            + " VALUES (1, ?, TRUE, NOW(), ?) ON CONFLICT (pool_id, user_id)"
            + " DO UPDATE SET paid = TRUE, paid_at = NOW()",
        userId,
        userId);
  }

  // ── tests ──────────────────────────────────────────────────────────────────

  @Test
  void requiresAuth() throws Exception {
    mockMvc.perform(get("/api/payments/my-subgroup")).andExpect(status().isUnauthorized());
  }

  @Test
  void captainSeesOwnInviteesWithTotals() throws Exception {
    // Mark P1 paid directly in DB so we have a known state before the GET.
    markPaidInDb(player1.getId());

    String token = jwt.issue(captain);
    mockMvc
        .perform(get("/api/payments/my-subgroup").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.members.length()").value(2))
        .andExpect(jsonPath("$.expectedCents").value(2 * 2000))
        .andExpect(jsonPath("$.collectedCents").value(2000))
        .andExpect(jsonPath("$.members[?(@.userId == " + player1.getId() + ")].paid").value(true))
        .andExpect(jsonPath("$.members[?(@.userId == " + player2.getId() + ")].paid").value(false));
  }

  @Test
  void captainMarksOwnPlayerPaid() throws Exception {
    String token = jwt.issue(captain);

    mockMvc
        .perform(
            put("/api/payments/" + player1.getId() + "/paid")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paid\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paid").value(true))
        .andExpect(jsonPath("$.userId").value(player1.getId()));

    // Re-GET and confirm collected updated.
    mockMvc
        .perform(get("/api/payments/my-subgroup").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.collectedCents").value(2000));
  }

  @Test
  void captainCannotMarkOtherCaptainsPlayer() throws Exception {
    // Captain2 tries to mark P1 (who was invited by captain, not captain2).
    String token = jwt.issue(captain2);

    mockMvc
        .perform(
            put("/api/payments/" + player1.getId() + "/paid")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paid\":true}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void playerCannotMarkAnyone() throws Exception {
    String token = jwt.issue(player1);

    mockMvc
        .perform(
            put("/api/payments/" + player2.getId() + "/paid")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paid\":true}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminCanMarkAnyone() throws Exception {
    String token = jwt.issue(admin);

    mockMvc
        .perform(
            put("/api/payments/" + player1.getId() + "/paid")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paid\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paid").value(true));
  }

  @Test
  void unknownMemberReturns404() throws Exception {
    String token = jwt.issue(admin);

    // Create a user that is NOT a pool member.
    var nonMember = saveUser("pmt-nonmember", "Non Member", UserRole.PLAYER);

    mockMvc
        .perform(
            put("/api/payments/" + nonMember.getId() + "/paid")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"paid\":true}"))
        .andExpect(status().isNotFound());
  }
}
