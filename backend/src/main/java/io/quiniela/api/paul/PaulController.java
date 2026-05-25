package io.quiniela.api.paul;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/paul")
public class PaulController {

  private final PaulService service;

  public PaulController(PaulService service) {
    this.service = service;
  }

  @PostMapping("/suggest")
  public ResponseEntity<PaulService.Suggestion> suggest(
      @AuthenticationPrincipal Jwt jwt, @RequestParam("matchId") Long matchId) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.suggestForMatch(matchId));
  }

  @PostMapping("/fill")
  public ResponseEntity<PaulService.FillResult> fillAll(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.fillAllForUser(userId));
  }
}
