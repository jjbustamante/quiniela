package io.quiniela.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import java.security.GeneralSecurityException;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The single auth endpoint. Called server-side by Next.js Auth.js after the user completes the
 * Google OIDC flow. We trade the Google ID token for our own session JWT.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

  private final GoogleTokenService googleTokenService;
  private final JwtService jwtService;
  private final UserRepository users;
  private final Set<String> adminEmails;

  public AuthController(
      GoogleTokenService googleTokenService,
      JwtService jwtService,
      UserRepository users,
      @Value("${app.admin-emails:}") String adminEmailsCsv) {
    this.googleTokenService = googleTokenService;
    this.jwtService = jwtService;
    this.users = users;
    this.adminEmails =
        adminEmailsCsv.isBlank()
            ? Set.of()
            : Set.of(adminEmailsCsv.toLowerCase().split("\\s*,\\s*"));
  }

  public record GoogleSignInRequest(String idToken) {}

  public record SessionResponse(
      String token, Long userId, String email, String name, boolean admin) {}

  @PostMapping("/google")
  @Transactional
  public ResponseEntity<?> signInWithGoogle(@RequestBody GoogleSignInRequest body) {
    if (body == null || body.idToken() == null || body.idToken().isBlank()) {
      return ResponseEntity.badRequest().body("Missing idToken");
    }

    Payload claims;
    try {
      claims = googleTokenService.verify(body.idToken());
    } catch (GeneralSecurityException e) {
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid Google ID token");
    }

    String googleSub = claims.getSubject();
    String email = claims.getEmail();
    String name = (String) claims.get("name");
    String picture = (String) claims.get("picture");
    boolean shouldBeAdmin = email != null && adminEmails.contains(email.toLowerCase());

    User user =
        users
            .findByGoogleSub(googleSub)
            .map(
                existing -> {
                  existing.setEmail(email);
                  existing.setDisplayName(name);
                  existing.setAvatarUrl(picture);
                  // Admin can only be promoted via env; never auto-demote
                  if (shouldBeAdmin && !existing.isAdmin()) {
                    existing.setAdmin(true);
                  }
                  return existing;
                })
            .orElseGet(() -> users.save(new User(googleSub, email, name, picture, shouldBeAdmin)));

    String sessionToken = jwtService.issue(user);

    return ResponseEntity.ok(
        new SessionResponse(
            sessionToken, user.getId(), user.getEmail(), user.getDisplayName(), user.isAdmin()));
  }
}
