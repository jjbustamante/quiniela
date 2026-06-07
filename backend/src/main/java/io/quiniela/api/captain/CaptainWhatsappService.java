package io.quiniela.api.captain;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Lets a captain (or the admin, for their direct invitees) control which of their PLAYER invitees
 * may see the community WhatsApp link. A caller may only read/flip users they personally invited.
 */
@Service
public class CaptainWhatsappService {

  private final UserRepository users;

  public CaptainWhatsappService(UserRepository users) {
    this.users = users;
  }

  public record RosterEntry(Long userId, String displayName, boolean visible) {}

  public record VisibilityRequest(Long userId, Boolean visible) {}

  @Transactional(readOnly = true)
  public List<RosterEntry> roster(Long callerId) {
    requireInviter(callerId);
    return users
        .findByInvitedByUserIdAndRoleOrderByDisplayNameAsc(callerId, UserRole.PLAYER)
        .stream()
        .map(
            u ->
                new RosterEntry(
                    u.getId(),
                    u.getDisplayName(),
                    Boolean.TRUE.equals(u.getWhatsappGroupVisible())))
        .toList();
  }

  @Transactional
  public void setVisibility(Long callerId, VisibilityRequest req) {
    requireInviter(callerId);
    if (req.userId() == null) {
      throw new IllegalArgumentException("userId is required");
    }
    User target =
        users
            .findById(req.userId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    if (!callerId.equals(target.getInvitedByUserId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your invitee");
    }
    target.setWhatsappGroupVisible(Boolean.TRUE.equals(req.visible()));
    users.save(target);
  }

  private void requireInviter(Long callerId) {
    User caller =
        users
            .findById(callerId)
            .orElseThrow(
                () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unknown user"));
    if (caller.getRole() != UserRole.ADMIN && caller.getRole() != UserRole.CAPTAIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Captains only");
    }
  }
}
