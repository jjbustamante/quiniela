package io.quiniela.api.user;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Single source of truth for the admin-only authorization guard.
 *
 * <p>Consolidates the role check that was previously copy-pasted into every admin-facing service
 * ({@code AdminResultsService}, {@code AdminTestService}, {@code AdminPaymentService}, {@code
 * AdminPoolConfigService}, {@code PaulAdminService}). The project deliberately uses an explicit
 * local guard rather than Spring's {@code @EnableMethodSecurity}/{@code @PreAuthorize}; this keeps
 * that choice but in one place, so changing what "admin" means (add a role, audit-log admin
 * actions, …) is a one-file edit.
 *
 * <p>Lives in the {@code user} package because the rule is about user roles and every caller
 * already depends on this package — no new cross-package dependency is introduced.
 */
@Component
public class AdminGuard {

  private final UserRepository users;

  public AdminGuard(UserRepository users) {
    this.users = users;
  }

  /**
   * Contract is unchanged from the previously inlined copies.
   *
   * @throws ResponseStatusException 401 UNAUTHORIZED if no user with {@code callerId} exists; 403
   *     FORBIDDEN if that user is not an {@link UserRole#ADMIN}.
   */
  public void requireAdmin(Long callerId) {
    User caller =
        users
            .findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (caller.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }
}
