package io.quiniela.api.me;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import java.time.ZoneId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

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
      Long invitedByUserId,
      String timezone) {}

  public record TimezoneRequest(String timezone) {}

  @GetMapping
  public ResponseEntity<MeResponse> me(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(toResponse(u));
  }

  @PutMapping("/timezone")
  public ResponseEntity<MeResponse> setTimezone(
      @AuthenticationPrincipal Jwt jwt, @RequestBody TimezoneRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    User u = users.findById(userId).orElse(null);
    if (u == null) return ResponseEntity.status(401).build();

    String tz = req.timezone();
    if (tz == null || !isValidZone(tz)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid timezone");
    }
    u.setTimezone(tz);
    users.save(u);
    return ResponseEntity.ok(toResponse(u));
  }

  private static boolean isValidZone(String tz) {
    try {
      ZoneId.of(tz);
      return true;
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static MeResponse toResponse(User u) {
    return new MeResponse(
        u.getId(),
        u.getEmail(),
        u.getDisplayName(),
        u.getAvatarUrl(),
        u.getRole().name(),
        u.getInvitePath(),
        u.getRole().canInvite(),
        u.getInvitedByUserId(),
        u.getTimezone());
  }
}
