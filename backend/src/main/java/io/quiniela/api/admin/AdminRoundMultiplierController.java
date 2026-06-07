package io.quiniela.api.admin;

import io.quiniela.api.admin.AdminRoundMultiplierService.MultipliersView;
import io.quiniela.api.admin.AdminRoundMultiplierService.UpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only per-round score multipliers (knockout rounds). */
@RestController
@RequestMapping("/api/admin/round-multipliers")
public class AdminRoundMultiplierController {

  private final AdminRoundMultiplierService service;

  public AdminRoundMultiplierController(AdminRoundMultiplierService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<MultipliersView> get(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getMultipliers(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping
  public ResponseEntity<MultipliersView> update(
      @AuthenticationPrincipal Jwt jwt, @RequestBody UpdateRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.updateMultipliers(Long.parseLong(jwt.getSubject()), req));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
