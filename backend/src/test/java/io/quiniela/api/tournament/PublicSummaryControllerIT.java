package io.quiniela.api.tournament;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class PublicSummaryControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired javax.sql.DataSource dataSource;

  MockMvc mockMvc;
  JdbcTemplate jdbc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    jdbc = new JdbcTemplate(dataSource);
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private User saveUser(String slug, String displayName, UserRole role) {
    var u = new User("g-ps-" + slug, slug + "@example.com", displayName, null, role);
    u.setInvitePath("ps-" + slug);
    return users.save(u);
  }

  private void addMember(Long userId) {
    jdbc.update(
        "INSERT INTO pool_membership (pool_id, user_id, joined_at)"
            + " VALUES (1, ?, NOW()) ON CONFLICT DO NOTHING",
        userId);
  }

  @Test
  void returnsSeededTournamentAndPool() throws Exception {
    mockMvc
        .perform(get("/api/public/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tournament.slug").value("fifa-wc-2026"))
        .andExpect(jsonPath("$.tournament.name").value("Copa Mundial FIFA 2026"))
        .andExpect(jsonPath("$.tournament.startDate").value("2026-06-11"))
        .andExpect(jsonPath("$.tournament.endDate").value("2026-07-19"))
        .andExpect(jsonPath("$.tournament.hostCountryCodes[0]").value("USA"))
        .andExpect(jsonPath("$.tournament.hostCountryCodes[1]").value("CAN"))
        .andExpect(jsonPath("$.tournament.hostCountryCodes[2]").value("MEX"))
        .andExpect(jsonPath("$.tournament.openingVenue").value("Estadio Azteca"))
        .andExpect(jsonPath("$.pool.currency").value("USD"))
        .andExpect(jsonPath("$.pool.entryFeeCents").value(2000))
        .andExpect(jsonPath("$.pool.potCents").isNumber())
        .andExpect(jsonPath("$.pool.panaCount").isNumber())
        // Seeded 80/15/5 split. Test pool starts empty → all payoutCents = 0
        // (proves the empty-pool edge case never 500s).
        .andExpect(jsonPath("$.prizeSplit.length()").value(3))
        .andExpect(jsonPath("$.prizeSplit[0].rank").value(1))
        .andExpect(jsonPath("$.prizeSplit[0].percentage").value(80))
        .andExpect(jsonPath("$.prizeSplit[0].payoutCents").value(0))
        .andExpect(jsonPath("$.prizeSplit[1].rank").value(2))
        .andExpect(jsonPath("$.prizeSplit[1].percentage").value(15))
        .andExpect(jsonPath("$.prizeSplit[2].rank").value(3))
        .andExpect(jsonPath("$.prizeSplit[2].percentage").value(5))
        .andExpect(jsonPath("$.testMode").isBoolean());
  }

  @Test
  void summaryIncludesKnockoutMultipliers() throws Exception {
    mockMvc
        .perform(get("/api/public/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.roundMultipliers.length()").value(6))
        .andExpect(jsonPath("$.roundMultipliers[0].code").value("R32"))
        .andExpect(jsonPath("$.roundMultipliers[0].multiplier").value(2));
  }

  /**
   * panaCount and potCents must reflect ONLY paying humans (non-admin, non-bot), even when an admin
   * account is also in pool_membership. Entry fee is 2000 cents (seeded by Flyway V003/V006).
   */
  @Test
  void panaCountExcludesAdminAndBot() throws Exception {
    // Seed 3 players (the paying humans).
    User p1 = saveUser("ps-p1", "PS Player 1", UserRole.PLAYER);
    User p2 = saveUser("ps-p2", "PS Player 2", UserRole.PLAYER);
    User p3 = saveUser("ps-p3", "PS Player 3", UserRole.PLAYER);
    addMember(p1.getId());
    addMember(p2.getId());
    addMember(p3.getId());

    // Also add an admin to pool_membership — must NOT be counted.
    User adminUser = saveUser("ps-adm", "PS Admin", UserRole.ADMIN);
    addMember(adminUser.getId());

    // Also add a bot user to pool_membership — must NOT be counted.
    User botUser = saveUser("ps-bot", "PS Bot", UserRole.PLAYER);
    jdbc.update("UPDATE users SET is_bot = true WHERE id = ?", botUser.getId());
    addMember(botUser.getId());

    // Pool entry_fee_cents is 2000 (seeded). Expect panaCount=3, potCents=6000.
    mockMvc
        .perform(get("/api/public/summary"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.pool.panaCount").value(3))
        .andExpect(jsonPath("$.pool.potCents").value(3 * 2000));
  }
}
