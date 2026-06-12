package io.quiniela.api.footballdata;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@TestPropertySource(properties = {"app.sync.token=test-token"})
class InternalSyncControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void rejectsWithoutToken() throws Exception {
    mockMvc.perform(post("/internal/sync/daily")).andExpect(status().isUnauthorized());
  }

  @Test
  void dailyWithTokenReturns200() throws Exception {
    mockMvc
        .perform(post("/internal/sync/daily").header("X-Sync-Token", "test-token"))
        .andExpect(status().isOk());
  }

  @Test
  void resultsWithTokenReturns200() throws Exception {
    mockMvc
        .perform(
            post("/internal/sync/results")
                .param("matchId", "999999")
                .header("X-Sync-Token", "test-token"))
        .andExpect(status().isOk());
  }
}
