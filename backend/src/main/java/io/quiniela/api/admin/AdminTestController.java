package io.quiniela.api.admin;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/test")
public class AdminTestController {

  private final AdminTestService service;

  public AdminTestController(AdminTestService service) {
    this.service = service;
  }

  public record ModeRequest(boolean enabled) {}

  public record DeadlinesRequest(String groupStageDeadline, String knockoutDeadline) {}

  private static Long callerId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }

  @GetMapping("/state")
  public ResponseEntity<AdminTestService.TestState> state(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getState(callerId(jwt)));
  }

  @PutMapping("/mode")
  public ResponseEntity<AdminTestService.ModeView> mode(
      @AuthenticationPrincipal Jwt jwt, @RequestBody ModeRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.setMode(callerId(jwt), req.enabled()));
  }

  @PostMapping("/clean")
  public ResponseEntity<AdminTestService.CleanResult> clean(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.clean(callerId(jwt)));
  }

  @PutMapping("/deadlines")
  public ResponseEntity<AdminTestService.DeadlinesView> deadlines(
      @AuthenticationPrincipal Jwt jwt, @RequestBody DeadlinesRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(
        service.setDeadlines(callerId(jwt), req.groupStageDeadline(), req.knockoutDeadline()));
  }
}
