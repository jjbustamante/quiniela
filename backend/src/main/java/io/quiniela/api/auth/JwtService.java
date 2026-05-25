package io.quiniela.api.auth;

import io.jsonwebtoken.Jwts;
import io.quiniela.api.user.User;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues our own session JWTs after successful Google sign-in. Signed with HMAC HS256 using the
 * shared {@code NEXTAUTH_SECRET} — the same key Auth.js uses for its own session-cookie signing.
 * Spring Security's resource server (see SecurityConfig) verifies inbound tokens with the same key,
 * so any token issued here is accepted on subsequent /api/** calls.
 *
 * <p>Token lifetime: 24h. Long enough to survive a friend's bracket-entry session; short enough
 * that a leaked token expires before kickoff of the next match window.
 */
@Service
public class JwtService {

  private static final String ISSUER = "quiniela-api";
  private static final Duration LIFETIME = Duration.ofHours(24);

  private final SecretKey signingKey;

  public JwtService(@Value("${app.security.jwt-secret}") String secret) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException(
          "app.security.jwt-secret must be at least 32 chars (HS256 minimum)");
    }
    this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }

  public String issue(User user) {
    Instant now = Instant.now();
    return Jwts.builder()
        .issuer(ISSUER)
        .subject(String.valueOf(user.getId()))
        .claim("email", user.getEmail())
        .claim("name", user.getDisplayName())
        .claim("admin", user.isAdmin())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(LIFETIME)))
        .signWith(signingKey, Jwts.SIG.HS256)
        .compact();
  }

  /** Accessor for the resource-server decoder (see SecurityConfig). */
  public SecretKey getSigningKey() {
    return signingKey;
  }
}
