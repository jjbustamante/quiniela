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

class AdminPaymentControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired JwtService jwt;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  JdbcTemplate jdbc;

  User admin;
  User captain;
  User player1;
  User player2;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);

    admin = saveUser("adm-a", "Admin A", UserRole.ADMIN);
    addMember(admin.getId());

    captain = saveUser("adm-cap", "Captain C", UserRole.CAPTAIN);
    addMember(captain.getId());

    player1 = saveUser("adm-p1", "Player P1", UserRole.PLAYER);
    addMember(player1.getId());
    jdbc.update(
        "UPDATE users SET invited_by_user_id = ? WHERE id = ?", captain.getId(), player1.getId());

    player2 = saveUser("adm-p2", "Player P2", UserRole.PLAYER);
    addMember(player2.getId());
    jdbc.update(
        "UPDATE users SET invited_by_user_id = ? WHERE id = ?", captain.getId(), player2.getId());
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

  private void insertPaidPayment(Long userId, Long markerId) {
    jdbc.update(
        "INSERT INTO payment (pool_id, user_id, paid, paid_at, marked_paid_by, created_at, updated_at)"
            + " VALUES (1, ?, true, NOW(), ?, NOW(), NOW())",
        userId,
        markerId);
  }

  // ── tests ──────────────────────────────────────────────────────────────────

  @Test
  void requiresAdmin() throws Exception {
    // A non-admin (captain) should receive 403.
    String token = jwt.issue(captain);
    mockMvc
        .perform(get("/api/admin/payments").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void requiresAuthForLedger() throws Exception {
    // No token → 401.
    mockMvc.perform(get("/api/admin/payments")).andExpect(status().isUnauthorized());
  }

  @Test
  void groupsByCaptainWithSubtotals() throws Exception {
    // Mark P1 paid so collectedCents = entry_fee_cents (2000 for pool 1).
    insertPaidPayment(player1.getId(), admin.getId());

    String token = jwt.issue(admin);
    mockMvc
        .perform(get("/api/admin/payments").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        // top-level counts include only paying humans: captain + p1 + p2 (admin excluded)
        .andExpect(jsonPath("$.memberCount").value(3))
        .andExpect(jsonPath("$.paidCount").value(1))
        .andExpect(jsonPath("$.potCents").value(3 * 2000))
        // captain group
        .andExpect(
            jsonPath("$.captains[?(@.captainId == " + captain.getId() + ")].members.length()")
                .value(2))
        .andExpect(
            jsonPath("$.captains[?(@.captainId == " + captain.getId() + ")].expectedCents")
                .value(4000))
        .andExpect(
            jsonPath("$.captains[?(@.captainId == " + captain.getId() + ")].collectedCents")
                .value(2000));
  }

  @Test
  void settledIsAdminOnly() throws Exception {
    // A captain trying to mark settled → 403.
    String token = jwt.issue(captain);
    mockMvc
        .perform(
            put("/api/admin/payments/" + captain.getId() + "/settled")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settled\":true}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminMarksCaptainSettled() throws Exception {
    String token = jwt.issue(admin);

    // Mark settled → 200, body has settled=true.
    mockMvc
        .perform(
            put("/api/admin/payments/" + captain.getId() + "/settled")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settled\":true}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.captainId").value(captain.getId()))
        .andExpect(jsonPath("$.settled").value(true));

    // Re-GET ledger → captain group shows captainSettled=true.
    mockMvc
        .perform(get("/api/admin/payments").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.captains[?(@.captainId == " + captain.getId() + ")].captainSettled")
                .value(true));
  }

  @Test
  void settledUnknownMember404() throws Exception {
    // Create a user that is NOT a pool member.
    User nonMember = saveUser("adm-nm", "Non Member", UserRole.PLAYER);

    String token = jwt.issue(admin);
    mockMvc
        .perform(
            put("/api/admin/payments/" + nonMember.getId() + "/settled")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"settled\":true}"))
        .andExpect(status().isNotFound());
  }

  /**
   * The ledger's memberCount, potCents, and orphan list must exclude the admin pool-member.
   *
   * <p>setUp seeds: admin + captain + player1 + player2 (all in pool_membership). Paying humans =
   * captain + player1 + player2 = 3. Admin must be invisible in counts and must NOT appear as an
   * orphan member.
   */
  @Test
  void ledgerExcludesAdminFromCountAndOrphans() throws Exception {
    String token = jwt.issue(admin);
    mockMvc
        .perform(get("/api/admin/payments").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        // Only the 3 paying humans (captain + 2 players) count — not the admin.
        .andExpect(jsonPath("$.memberCount").value(3))
        .andExpect(jsonPath("$.potCents").value(3 * 2000))
        // Admin must not appear in orphans.
        .andExpect(jsonPath("$.orphans[?(@.role == 'ADMIN')]").isEmpty());
  }
}
