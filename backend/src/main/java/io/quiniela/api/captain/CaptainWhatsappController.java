package io.quiniela.api.captain;

import io.quiniela.api.captain.CaptainWhatsappService.RosterEntry;
import io.quiniela.api.captain.CaptainWhatsappService.VisibilityRequest;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Captain/admin: manage which of their PLAYER invitees may see the WhatsApp group link. */
@RestController
@RequestMapping("/api/captain")
public class CaptainWhatsappController {

  private final CaptainWhatsappService service;

  public CaptainWhatsappController(CaptainWhatsappService service) {
    this.service = service;
  }

  @GetMapping("/whatsapp-roster")
  public ResponseEntity<List<RosterEntry>> roster(@AuthenticationPrincipal Jwt jwt) {
    if (jwt == null) return ResponseEntity.status(401).build();
    return ResponseEntity.ok(service.roster(Long.parseLong(jwt.getSubject())));
  }

  @PutMapping("/whatsapp-visibility")
  public ResponseEntity<Void> setVisibility(
      @AuthenticationPrincipal Jwt jwt, @RequestBody VisibilityRequest req) {
    if (jwt == null) return ResponseEntity.status(401).build();
    service.setVisibility(Long.parseLong(jwt.getSubject()), req);
    return ResponseEntity.ok().build();
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> handleBadInput(IllegalArgumentException e) {
    return ResponseEntity.badRequest().body(e.getMessage());
  }
}
