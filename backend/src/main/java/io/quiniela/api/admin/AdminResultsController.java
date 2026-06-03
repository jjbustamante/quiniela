package io.quiniela.api.admin;

import io.quiniela.api.admin.AdminResultsService.AdminMatchRow;
import io.quiniela.api.admin.AdminResultsService.MatchResultView;
import io.quiniela.api.admin.AdminResultsService.RecordResultCommand;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminResultsController {

  private final AdminResultsService service;

  public AdminResultsController(AdminResultsService service) {
    this.service = service;
  }

  public record RecordResultRequest(int scoreT1, int scoreT2, Long advancingTeamId) {}

  @GetMapping("/matches")
  public ResponseEntity<List<AdminMatchRow>> listAllMatches(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long callerUserId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.listAllMatches(callerUserId));
  }

  @PutMapping("/matches/{matchId}/result")
  public ResponseEntity<MatchResultView> record(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long matchId,
      @RequestBody RecordResultRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long callerUserId = Long.parseLong(jwt.getSubject());
    MatchResultView view =
        service.record(
            new RecordResultCommand(
                callerUserId, matchId, req.scoreT1(), req.scoreT2(), req.advancingTeamId()));
    return ResponseEntity.ok(view);
  }
}
