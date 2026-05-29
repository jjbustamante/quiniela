package io.quiniela.api.payment;

import io.quiniela.api.payment.AdminPaymentService.LedgerView;
import io.quiniela.api.payment.AdminPaymentService.SettledRequest;
import io.quiniela.api.payment.AdminPaymentService.SettledRowView;
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
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

  private final AdminPaymentService service;

  public AdminPaymentController(AdminPaymentService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<LedgerView> getLedger(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long callerId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.getLedger(callerId));
  }

  @PutMapping("/{captainId}/settled")
  public ResponseEntity<SettledRowView> markSettled(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long captainId,
      @RequestBody SettledRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    Long callerId = Long.parseLong(jwt.getSubject());
    return ResponseEntity.ok(service.markSettled(callerId, captainId, req.settled()));
  }
}
