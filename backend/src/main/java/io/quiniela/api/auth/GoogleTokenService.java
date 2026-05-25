package io.quiniela.api.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken.Payload;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.security.GeneralSecurityException;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Verifies Google-issued ID tokens (from the Next.js Auth.js OAuth callback).
 *
 * <p>Validation checks done by GoogleIdTokenVerifier:
 *
 * <ul>
 *   <li>JWT signature against Google's published JWKS (rotated automatically)
 *   <li>{@code iss} claim is accounts.google.com or https://accounts.google.com
 *   <li>{@code aud} claim matches our OAuth client ID
 *   <li>{@code exp} is in the future
 * </ul>
 *
 * <p>Returns the verified {@link Payload} (Google's claims about the user) or null if any check
 * fails.
 */
@Service
public class GoogleTokenService {

  private final GoogleIdTokenVerifier verifier;

  public GoogleTokenService(@Value("${app.google.oauth-client-id}") String oauthClientId) {
    this.verifier =
        new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
            .setAudience(Collections.singletonList(oauthClientId))
            .build();
  }

  /** Returns the verified payload, or throws if the token is invalid. */
  public Payload verify(String idTokenString) throws GeneralSecurityException {
    try {
      GoogleIdToken token = verifier.verify(idTokenString);
      if (token == null) {
        throw new GeneralSecurityException("Google ID token failed verification");
      }
      return token.getPayload();
    } catch (java.io.IOException e) {
      // Network failure reaching Google's JWKS — surface as auth failure
      throw new GeneralSecurityException("Unable to verify Google ID token", e);
    }
  }
}
