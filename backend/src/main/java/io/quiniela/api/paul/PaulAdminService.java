package io.quiniela.api.paul;

import io.quiniela.api.user.AdminGuard;
import org.springframework.stereotype.Service;

@Service
public class PaulAdminService {

  private final AdminGuard adminGuard;
  private final PaulJobService jobService;
  private final PaulService paulService;

  public PaulAdminService(
      AdminGuard adminGuard, PaulJobService jobService, PaulService paulService) {
    this.adminGuard = adminGuard;
    this.jobService = jobService;
    this.paulService = paulService;
  }

  /**
   * Start candidate generation in the background. Returns the initial job status (202 at the API).
   */
  public PaulJobStatus generate(Long callerId) {
    adminGuard.requireAdmin(callerId);
    return jobService.startGenerate();
  }

  /** Start official-pick synthesis in the background. Returns the initial job status (202). */
  public PaulJobStatus synthesize(Long callerId) {
    adminGuard.requireAdmin(callerId);
    return jobService.startSynthesize();
  }

  /** Current/last batch job status, for polling progress. */
  public PaulJobStatus jobStatus(Long callerId) {
    adminGuard.requireAdmin(callerId);
    return jobService.current();
  }

  /** Reveal is DB-only (no LLM calls), so it stays synchronous. */
  public PaulService.RevealResult reveal(Long callerId) {
    adminGuard.requireAdmin(callerId);
    return paulService.reveal();
  }
}
