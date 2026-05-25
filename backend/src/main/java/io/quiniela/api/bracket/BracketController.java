package io.quiniela.api.bracket;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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
}
