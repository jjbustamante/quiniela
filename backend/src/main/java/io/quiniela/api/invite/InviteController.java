package io.quiniela.api.invite;

import io.quiniela.api.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/invite")
public class InviteController {

  private final UserRepository users;

  public InviteController(UserRepository users) {
    this.users = users;
  }

  public record InviteResolution(boolean valid, String inviterDisplayName, String inviterRole) {}

  @GetMapping("/{invitePath}")
  public ResponseEntity<InviteResolution> resolve(@PathVariable String invitePath) {
    return users
        .findByInvitePath(invitePath)
        .filter(u -> u.getRole().canInvite())
        .map(
            u ->
                ResponseEntity.ok(
                    new InviteResolution(true, u.getDisplayName(), u.getRole().name())))
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
