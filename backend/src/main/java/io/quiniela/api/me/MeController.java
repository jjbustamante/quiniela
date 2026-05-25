package io.quiniela.api.me;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class MeController {

  private final UserRepository users;

  public MeController(UserRepository users) {
    this.users = users;
  }

  public record MeResponse(
      Long id,
      String email,
      String displayName,
      String avatarUrl,
      String role,
      String invitePath,
      boolean canInvite,
      Long invitedByUserId) {}

  @GetMapping
  public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(
        new MeResponse(
            u.getId(),
            u.getEmail(),
            u.getDisplayName(),
            u.getAvatarUrl(),
            u.getRole().name(),
            u.getInvitePath(),
            u.getRole().canInvite(),
            u.getInvitedByUserId()));
  }
}
