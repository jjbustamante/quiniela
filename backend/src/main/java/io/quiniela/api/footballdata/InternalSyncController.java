package io.quiniela.api.footballdata;

import io.quiniela.api.footballdata.FootballDataSyncService.SyncResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Machine-facing sync endpoints. Auth is the shared-secret header enforced by SyncTokenFilter (not
 * JWT). Always 200 for handled outcomes so Cloud Scheduler / Cloud Tasks don't retry-storm.
 */
@RestController
@RequestMapping("/internal/sync")
public class InternalSyncController {

  private final FootballDataSyncService service;

  public InternalSyncController(FootballDataSyncService service) {
    this.service = service;
  }

  @PostMapping("/daily")
  public ResponseEntity<SyncResult> daily() {
    return ResponseEntity.ok(service.runDaily());
  }

  @PostMapping("/results")
  public ResponseEntity<SyncResult> results(@RequestParam long matchId) {
    return ResponseEntity.ok(service.syncMatch(matchId));
  }

  @PostMapping("/fixtures")
  public ResponseEntity<SyncResult> fixtures() {
    return ResponseEntity.ok(service.syncFull());
  }
}
