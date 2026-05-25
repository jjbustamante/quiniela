package io.quiniela.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import io.quiniela.api.pool.PoolMembershipRepository;
import io.quiniela.api.support.AbstractIntegrationTest;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class AuthControllerIT extends AbstractIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired UserRepository users;
  @Autowired PoolMembershipRepository memberships;
  @MockitoBean GoogleTokenService googleTokenService;

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
  }

  @Test
  void firstSignInWithoutInvitePathRejected() throws Exception {
    var payload = new GoogleIdToken.Payload();
    payload.setSubject("google-sub-stranger");
    payload.setEmail("stranger@example.com");
    payload.set("name", "Stranger");
    payload.set("picture", "https://example.com/p.jpg");
    when(googleTokenService.verify(any())).thenReturn(payload);

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"any\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void firstSignInWithAdminEmailBecomesAdminAndJoinsPool() throws Exception {
    var payload = new GoogleIdToken.Payload();
    payload.setSubject("google-sub-admin");
    payload.setEmail("admin@example.com");
    payload.set("name", "Juan");
    when(googleTokenService.verify(any())).thenReturn(payload);

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"any\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("ADMIN"))
        .andExpect(jsonPath("$.invitePath").isNotEmpty());

    var admin = users.findByEmail("admin@example.com").orElseThrow();
    assertThat(memberships.existsByPoolIdAndUserId(1L, admin.getId())).isTrue();
  }

  @Test
  void signUpViaCaptainInviteBecomesPlayer() throws Exception {
    var captain = new User("g-captain", "cap@example.com", "Cap", null, UserRole.CAPTAIN);
    captain.setInvitePath("cap-abc123");
    captain = users.save(captain);

    var payload = new GoogleIdToken.Payload();
    payload.setSubject("g-friend");
    payload.setEmail("friend@example.com");
    payload.set("name", "Friend");
    when(googleTokenService.verify(any())).thenReturn(payload);

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"any\",\"invitePath\":\"cap-abc123\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("PLAYER"));

    var friend = users.findByEmail("friend@example.com").orElseThrow();
    assertThat(friend.getInvitedByUserId()).isEqualTo(captain.getId());
    assertThat(friend.getInvitePath()).isNull(); // players don't get a personal path
  }

  @Test
  void signUpViaAdminInviteBecomesCaptainWithOwnPath() throws Exception {
    var admin = new User("g-admin", "admin@example.com", "Juan", null, UserRole.ADMIN);
    admin.setInvitePath("juan-xyz789");
    admin = users.save(admin);

    var payload = new GoogleIdToken.Payload();
    payload.setSubject("g-newcap");
    payload.setEmail("andres@example.com");
    payload.set("name", "Andrés");
    when(googleTokenService.verify(any())).thenReturn(payload);

    mockMvc
        .perform(
            post("/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\":\"any\",\"invitePath\":\"juan-xyz789\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("CAPTAIN"))
        .andExpect(jsonPath("$.invitePath").isNotEmpty());

    var captain = users.findByEmail("andres@example.com").orElseThrow();
    assertThat(memberships.existsByPoolIdAndUserId(1L, captain.getId())).isTrue();
  }
}
