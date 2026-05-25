package io.quiniela.api.config;

import io.quiniela.api.auth.JwtService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 7 wiring.
 *
 * <ul>
 *   <li>Stateless: no server-side session, every request is authenticated by its bearer JWT.
 *   <li>CSRF disabled — pure REST API consumed by Next.js server-side, no browser-submitted forms.
 *   <li>Public: /actuator/** (health probes) + /auth/** (sign-in) + /api/invite/** (invite
 *       resolver).
 *   <li>Everything else (/api/**, future endpoints) requires a valid JWT issued by {@link
 *       JwtService}.
 * </ul>
 */
@Configuration
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http, JwtDecoder jwtDecoder)
      throws Exception {
    http.csrf(csrf -> csrf.disable())
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(
            authz ->
                authz
                    .requestMatchers("/actuator/**", "/auth/**", "/api/invite/**")
                    .permitAll()
                    .anyRequest()
                    .authenticated())
        .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
    return http.build();
  }

  /**
   * JWT decoder backed by the same HMAC key {@link JwtService} signs with. Symmetric on purpose —
   * the api both mints and verifies its own session tokens.
   */
  @Bean
  public JwtDecoder jwtDecoder(JwtService jwtService) {
    return NimbusJwtDecoder.withSecretKey(jwtService.getSigningKey()).build();
  }
}
