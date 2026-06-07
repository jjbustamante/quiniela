package io.quiniela.api.ranking;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** A player's points by stage + per-match breakdown. Any pool member is viewable. */
@RestController
@RequestMapping("/api/ranking")
public class ScorecardController {

  private final ScorecardService service;

  public ScorecardController(ScorecardService service) {
    this.service = service;
  }

  @GetMapping("/{userId}/scorecard")
  public ResponseEntity<ScorecardView> scorecard(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long userId) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getScorecard(userId));
  }
}
