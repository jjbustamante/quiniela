package io.quiniela.api.matches;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matches")
public class MatchesController {

  private final MatchesService service;

  public MatchesController(MatchesService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<MatchesService.MatchesView> get(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long callerUserId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getMatches(callerUserId));
  }
}
