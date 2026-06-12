package io.quiniela.api.footballdata;

import io.quiniela.api.footballdata.FootballDataSyncService.SyncResult;
import io.quiniela.api.user.AdminGuard;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only manual triggers for the results sync (testing / catch-up). Reuses AdminGuard +
 * Google-JWT auth — no shared secret. Mirrors PaulAdminController.
 */
@RestController
@RequestMapping("/api/admin/sync")
public class AdminSyncController {

  private final FootballDataSyncService service;
  private final AdminGuard adminGuard;

  public AdminSyncController(FootballDataSyncService service, AdminGuard adminGuard) {
    this.service = service;
    this.adminGuard = adminGuard;
  }

  private static Long callerId(Jwt jwt) {
    return Long.parseLong(jwt.getSubject());
  }

  /** Refresh fixtures and enqueue today's per-match result checks now. */
  @PostMapping("/daily")
  public ResponseEntity<SyncResult> daily(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    adminGuard.requireAdmin(callerId(jwt));
    return ResponseEntity.ok(service.runDaily());
  }

  /** Force an immediate result check for one match (bypasses the task schedule). */
  @PostMapping("/match/{matchId}")
  public ResponseEntity<SyncResult> match(
      @AuthenticationPrincipal Jwt jwt, @PathVariable long matchId) {
    if (jwt == null) return ResponseEntity.status(401).build();
    adminGuard.requireAdmin(callerId(jwt));
    return ResponseEntity.ok(service.syncMatch(matchId));
  }
}
