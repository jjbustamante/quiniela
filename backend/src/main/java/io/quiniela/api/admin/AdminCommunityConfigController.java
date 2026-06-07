package io.quiniela.api.admin;

import io.quiniela.api.admin.AdminCommunityConfigService.CommunityConfigView;
import io.quiniela.api.admin.AdminCommunityConfigService.UpdateRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-only community config: WhatsApp group link + master switch. */
@RestController
@RequestMapping("/api/admin/community-config")
public class AdminCommunityConfigController {

  private final AdminCommunityConfigService service;

  public AdminCommunityConfigController(AdminCommunityConfigService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<CommunityConfigView> get(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.getConfig(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping
  public ResponseEntity<CommunityConfigView> update(
      @AuthenticationPrincipal Jwt jwt, @RequestBody UpdateRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.updateConfig(Long.parseLong(jwt.getSubject()), req));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
