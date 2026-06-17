package io.quiniela.api.compare;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/compare")
public class CompareController {

  private final CompareService service;

  public CompareController(CompareService service) {
    this.service = service;
  }

  @GetMapping("/group")
  public ResponseEntity<CompareService.GroupConsensusView> group(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getGroupConsensus(userId));
  }

  @GetMapping("/h2h")
  public ResponseEntity<CompareService.H2HView> h2h(
      @AuthenticationPrincipal Jwt jwt, @RequestParam(value = "vs", required = false) Long vs) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getH2H(userId, vs));
  }

  @GetMapping("/match/{matchId}/picks")
  public ResponseEntity<CompareService.MatchPicksView> matchPicks(
      @AuthenticationPrincipal Jwt jwt, @PathVariable Long matchId) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getMatchPicks(userId, matchId));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
