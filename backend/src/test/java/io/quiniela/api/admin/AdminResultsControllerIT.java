package io.quiniela.api.admin;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.quiniela.api.auth.JwtService;
import io.quiniela.api.match.Match;
import io.quiniela.api.match.MatchRepository;
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

class AdminResultsControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired MatchRepository matches;
  @Autowired JwtService jwt;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  private String adminToken() {
    var admin = new User("g-admin-rs", "admin-rs@example.com", "Admin RS", null, UserRole.ADMIN);
    admin.setInvitePath("admin-rs-1");
    admin = users.save(admin);
    return jwt.issue(admin);
  }

  private String playerToken() {
    var player =
        new User("g-player-rs", "player-rs@example.com", "Player RS", null, UserRole.PLAYER);
    player.setInvitePath("player-rs-1");
    player = users.save(player);
    return jwt.issue(player);
  }

  @Test
  void adminCanRecordResult() throws Exception {
    String token = adminToken();
    Match before = matches.findById(1L).orElseThrow();

    mockMvc
        .perform(
            put("/api/admin/matches/1/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":2,\"scoreT2\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.matchId").value(1))
        .andExpect(jsonPath("$.scoreT1").value(2))
        .andExpect(jsonPath("$.scoreT2").value(1))
        .andExpect(jsonPath("$.played").value(true))
        .andExpect(jsonPath("$.winnerId").value(before.getTeam1Id()));

    Match after = matches.findById(1L).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(after.getScoreT1()).isEqualTo(2);
    org.assertj.core.api.Assertions.assertThat(after.getScoreT2()).isEqualTo(1);
    org.assertj.core.api.Assertions.assertThat(after.getPlayed()).isTrue();
    org.assertj.core.api.Assertions.assertThat(after.getWinnerId()).isEqualTo(before.getTeam1Id());
  }

  @Test
  void drawSetsWinnerIdToNull() throws Exception {
    String token = adminToken();

    mockMvc
        .perform(
            put("/api/admin/matches/2/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":1,\"scoreT2\":1}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.winnerId").doesNotExist());

    Match after = matches.findById(2L).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(after.getWinnerId()).isNull();
  }

  @Test
  void correctionReplacesPreviousScoreAndWinner() throws Exception {
    String token = adminToken();
    Match m = matches.findById(3L).orElseThrow();

    mockMvc
        .perform(
            put("/api/admin/matches/3/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":3,\"scoreT2\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.winnerId").value(m.getTeam1Id()));

    // Correction: swap winner to team2.
    mockMvc
        .perform(
            put("/api/admin/matches/3/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":0,\"scoreT2\":2}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.scoreT1").value(0))
        .andExpect(jsonPath("$.scoreT2").value(2))
        .andExpect(jsonPath("$.winnerId").value(m.getTeam2Id()));
  }

  @Test
  void playerGetsForbidden() throws Exception {
    String token = playerToken();

    mockMvc
        .perform(
            put("/api/admin/matches/1/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":2,\"scoreT2\":1}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void unauthenticatedGetsUnauthorized() throws Exception {
    mockMvc
        .perform(
            put("/api/admin/matches/1/result")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":2,\"scoreT2\":1}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void unknownMatchReturnsNotFound() throws Exception {
    String token = adminToken();

    mockMvc
        .perform(
            put("/api/admin/matches/999999/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":1,\"scoreT2\":0}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void adminListAllReturns104Matches() throws Exception {
    String token = adminToken();

    mockMvc
        .perform(get("/api/admin/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(104))
        .andExpect(jsonPath("$[0].matchId").isNumber())
        .andExpect(jsonPath("$[0].roundCode").isString())
        .andExpect(jsonPath("$[0].team1").exists());
  }

  @Test
  void playerListAllForbidden() throws Exception {
    String token = playerToken();

    mockMvc
        .perform(get("/api/admin/matches").header("Authorization", "Bearer " + token))
        .andExpect(status().isForbidden());
  }

  @Test
  void negativeScoreRejected() throws Exception {
    String token = adminToken();

    mockMvc
        .perform(
            put("/api/admin/matches/1/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":-1,\"scoreT2\":0}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void recordingDrawWithAdvancingTeamStoresAdvancedTeamId() throws Exception {
    String token = adminToken();
    Match before = matches.findById(2L).orElseThrow();
    Long team2 = before.getTeam2Id();

    mockMvc
        .perform(
            put("/api/admin/matches/2/result")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"scoreT1\":1,\"scoreT2\":1,\"advancingTeamId\":" + team2 + "}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.winnerId").doesNotExist());

    Match after = matches.findById(2L).orElseThrow();
    org.assertj.core.api.Assertions.assertThat(after.getAdvancedTeamId()).isEqualTo(team2);
    org.assertj.core.api.Assertions.assertThat(after.getWinnerId()).isNull();
  }
}
