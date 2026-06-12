package io.quiniela.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards /internal/** with a shared-secret header. The Cloud Run service is publicly invokable
 * (allUsers), so IAM can't gate this route — the secret is checked in-app. Fails closed when no
 * token is configured. Constant-time comparison to avoid timing leaks.
 */
@Component
public class SyncTokenFilter extends OncePerRequestFilter {

  private final String configuredToken;

  public SyncTokenFilter(@Value("${app.sync.token:}") String configuredToken) {
    this.configuredToken = configuredToken == null ? "" : configuredToken;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/internal/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String provided = request.getHeader("X-Sync-Token");
    if (configuredToken.isEmpty()
        || provided == null
        || !constantTimeEquals(configuredToken, provided)) {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
      return;
    }
    chain.doFilter(request, response);
  }

  private static boolean constantTimeEquals(String a, String b) {
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
