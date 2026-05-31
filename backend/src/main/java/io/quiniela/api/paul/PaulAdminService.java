package io.quiniela.api.paul;

import io.quiniela.api.user.User;
import io.quiniela.api.user.UserRepository;
import io.quiniela.api.user.UserRole;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaulAdminService {

  private final UserRepository users;
  private final PaulJobService jobService;
  private final PaulService paulService;

  public PaulAdminService(
      UserRepository users, PaulJobService jobService, PaulService paulService) {
    this.users = users;
    this.jobService = jobService;
    this.paulService = paulService;
  }

  void requireAdmin(Long callerId) {
    User caller =
        users
            .findById(callerId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    if (caller.getRole() != UserRole.ADMIN) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
    }
  }

  /**
   * Start candidate generation in the background. Returns the initial job status (202 at the API).
   */
  public PaulJobStatus generate(Long callerId) {
    requireAdmin(callerId);
    return jobService.startGenerate();
  }

  /** Start official-pick synthesis in the background. Returns the initial job status (202). */
  public PaulJobStatus synthesize(Long callerId) {
    requireAdmin(callerId);
    return jobService.startSynthesize();
  }

  /** Current/last batch job status, for polling progress. */
  public PaulJobStatus jobStatus(Long callerId) {
    requireAdmin(callerId);
    return jobService.current();
  }

  /** Reveal is DB-only (no LLM calls), so it stays synchronous. */
  public PaulService.RevealResult reveal(Long callerId) {
    requireAdmin(callerId);
    return paulService.reveal();
  }
}
