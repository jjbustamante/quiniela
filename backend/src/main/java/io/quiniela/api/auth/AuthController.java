package io.quiniela.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import io.quiniela.api.invite.InvitePathGenerator;
import io.quiniela.api.pool.PoolMembership;
import io.quiniela.api.pool.PoolMembershipRepository;
import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
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

  private static final Long DEFAULT_POOL_ID = 1L;

  private final GoogleTokenService googleTokenService;
  private final JwtService jwtService;
  private final UserRepository users;
  private final PoolMembershipRepository memberships;
  private final InvitePathGenerator pathGenerator;
  private final Set<String> adminEmails;

  public AuthController(
      GoogleTokenService googleTokenService,
      JwtService jwtService,
      UserRepository users,
      PoolMembershipRepository memberships,
      InvitePathGenerator pathGenerator,
      @Value("${app.admin-emails:}") String adminEmailsCsv) {
    this.googleTokenService = googleTokenService;
    this.jwtService = jwtService;
    this.users = users;
    this.memberships = memberships;
    this.pathGenerator = pathGenerator;
    this.adminEmails =
        adminEmailsCsv.isBlank()
            ? Set.of()
            : Set.of(adminEmailsCsv.toLowerCase().split("\\s*,\\s*"));
  }

  public record GoogleSignInRequest(String idToken, String invitePath) {}

  public record SessionResponse(
      String token, Long userId, String email, String name, String role, String invitePath) {}

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

    var existing = users.findByGoogleSub(googleSub);
    User user;
    if (existing.isPresent()) {
      // Returning user — refresh profile, ignore invitePath.
      user = existing.get();
      user.setEmail(email);
      user.setDisplayName(name);
      user.setAvatarUrl(picture);
    } else {
      // New user — must have a path in: admin-email match OR a valid invitePath.
      UserRole role;
      Long inviterId = null;

      if (email != null && adminEmails.contains(email.toLowerCase())) {
        role = UserRole.ADMIN;
      } else if (body.invitePath() != null && !body.invitePath().isBlank()) {
        var inviter = users.findByInvitePath(body.invitePath());
        if (inviter.isEmpty()) {
          return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Invitación no válida");
        }
        role = inviter.get().getRole().invitee();
        inviterId = inviter.get().getId();
      } else {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Necesitas una invitación");
      }

      user = new User(googleSub, email, name, picture, role);
      user.setInvitedByUserId(inviterId);

      // Admin + captain get a personal invite path; players don't.
      if (role.canInvite()) {
        user.setInvitePath(generateUniquePath(name));
      }

      user = users.save(user);

      // Join the default pool.
      if (!memberships.existsByPoolIdAndUserId(DEFAULT_POOL_ID, user.getId())) {
        memberships.save(new PoolMembership(DEFAULT_POOL_ID, user.getId()));
      }
    }

    String sessionToken = jwtService.issue(user);

    return ResponseEntity.ok(
        new SessionResponse(
            sessionToken,
            user.getId(),
            user.getEmail(),
            user.getDisplayName(),
            user.getRole().name(),
            user.getInvitePath()));
  }

  private String generateUniquePath(String name) {
    // Retry up to 5 times for the (vanishingly rare) collision case.
    for (int i = 0; i < 5; i++) {
      String candidate = pathGenerator.generate(name);
      if (users.findByInvitePath(candidate).isEmpty()) return candidate;
    }
    throw new IllegalStateException("Could not mint a unique invite path");
  }
}
