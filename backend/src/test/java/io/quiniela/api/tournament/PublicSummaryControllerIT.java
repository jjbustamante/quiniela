package io.quiniela.api.tournament;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class PublicSummaryControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
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
}
