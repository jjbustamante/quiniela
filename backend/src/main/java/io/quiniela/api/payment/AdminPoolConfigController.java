package io.quiniela.api.payment;

import io.quiniela.api.payment.AdminPoolConfigService.PoolConfigView;
import io.quiniela.api.payment.AdminPoolConfigService.UpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only money configuration: entry fee, house cut, prize split. */
@RestController
@RequestMapping("/api/admin/pool-config")
public class AdminPoolConfigController {

  private final AdminPoolConfigService service;

  public AdminPoolConfigController(AdminPoolConfigService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<PoolConfigView> get(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getConfig(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping
  public ResponseEntity<PoolConfigView> update(
      @AuthenticationPrincipal Jwt jwt, @RequestBody UpdateRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.updateConfig(Long.parseLong(jwt.getSubject()), req));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
