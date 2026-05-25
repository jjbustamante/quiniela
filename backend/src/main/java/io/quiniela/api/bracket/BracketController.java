package io.quiniela.api.bracket;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bracket")
public class BracketController {

  private final BracketService service;

  public BracketController(BracketService service) {
    this.service = service;
  }

  @GetMapping("/me")
  public ResponseEntity<BracketService.BracketView> getMyBracket(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getMyBracket(userId));
  }

  @PostMapping("/bet")
  public ResponseEntity<BracketService.BetView> saveBet(
      @AuthenticationPrincipal Jwt jwt, @RequestBody BracketService.SaveBetRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long userId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.saveBet(userId, req));
  }

  @ExceptionHandler(BracketService.BracketLockedException.class)
  public ResponseEntity<String> handleLocked(BracketService.BracketLockedException e) {
    return ResponseEntity.status(HttpStatus.LOCKED).body(e.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
