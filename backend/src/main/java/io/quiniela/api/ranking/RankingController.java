package io.quiniela.api.ranking;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ranking")
public class RankingController {

  private final RankingService service;

  public RankingController(RankingService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<RankingView> get(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long callerUserId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getRanking(callerUserId));
  }
}
