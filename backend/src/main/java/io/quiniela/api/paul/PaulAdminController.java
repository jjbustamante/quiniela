package io.quiniela.api.paul;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/paul")
public class PaulAdminController {

  private final PaulAdminService service;

  public PaulAdminController(PaulAdminService service) {
    this.service = service;
  }

  private static Long callerId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }

  @PostMapping("/generate")
  public ResponseEntity<PaulAdminService.GenerateResult> generate(
      @AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.generate(callerId(jwt)));
  }

  @PostMapping("/synthesize")
  public ResponseEntity<PaulAdminService.SynthesizeResult> synthesize(
      @AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.synthesize(callerId(jwt)));
  }

  @PostMapping("/reveal")
  public ResponseEntity<PaulService.RevealResult> reveal(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.reveal(callerId(jwt)));
  }
}
