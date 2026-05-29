package io.quiniela.api.payment;

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
@RequestMapping("/api/payments")
public class PaymentController {

  private final PaymentService service;

  public PaymentController(PaymentService service) {
    this.service = service;
  }

  @GetMapping("/my-subgroup")
  public ResponseEntity<PaymentService.SubgroupView> mySubgroup(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.mySubgroup(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping("/{userId}/paid")
  public ResponseEntity<PaymentService.PaymentRowView> markPaid(
      @AuthenticationPrincipal Jwt jwt,
      @PathVariable Long userId,
      @RequestBody PaymentService.MarkPaidRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.markPaid(Long.parseLong(jwt.getSubject()), userId, req));
  }
}
