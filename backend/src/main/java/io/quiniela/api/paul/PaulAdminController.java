package io.quiniela.api.paul;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
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

  /** Kicks off candidate generation in the background; returns 202 with the initial job status. */
  @PostMapping("/generate")
  public ResponseEntity<PaulJobStatus> generate(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.accepted().body(service.generate(callerId(jwt)));
  }

  /** Kicks off official-pick synthesis in the background; returns 202 with the initial status. */
  @PostMapping("/synthesize")
  public ResponseEntity<PaulJobStatus> synthesize(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.accepted().body(service.synthesize(callerId(jwt)));
  }

  /** Poll the current/last batch job's progress. */
  @GetMapping("/status")
  public ResponseEntity<PaulJobStatus> status(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.jobStatus(callerId(jwt)));
  }

  @PostMapping("/reveal")
  public ResponseEntity<PaulService.RevealResult> reveal(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.reveal(callerId(jwt)));
  }
}
