package io.quiniela.api.paul;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Runs Paul's long batch jobs (generate candidates, synthesize officials) off the HTTP request
 * thread, tracking live progress. Both jobs make dozens of sequential LLM calls; running them
 * inline would blow the Cloud Run request timeout and pin a DB connection for minutes. The admin
 * endpoints start a job (202) and poll {@link #current()} for progress.
 *
 * <p>Only one job runs at a time — a second start while one is running fails with 409.
 */
@Service
public class PaulJobService {

  private final PaulJobExecutor executor;
  private final PaulPredictionService predictionService;
  private final PaulEnsembleService ensembleService;

  private final AtomicReference<PaulJobStatus> status = new AtomicReference<>(PaulJobStatus.idle());
  private final AtomicBoolean running = new AtomicBoolean(false);

  public PaulJobService(
      PaulJobExecutor executor,
      PaulPredictionService predictionService,
      PaulEnsembleService ensembleService) {
    this.executor = executor;
    this.predictionService = predictionService;
    this.ensembleService = ensembleService;
  }

  public PaulJobStatus startGenerate() {
    return start("generate", predictionService::generateAllGroup);
  }

  public PaulJobStatus startSynthesize() {
    return start("synthesize", ensembleService::synthesizeAllGroup);
  }

  public PaulJobStatus current() {
    return status.get();
  }

  private PaulJobStatus start(String phase, Consumer<PaulProgress> work) {
    if (!running.compareAndSet(false, true)) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT, "A Paul job is already running: " + status.get().phase());
    }
    status.set(PaulJobStatus.running(phase, Instant.now()));
    Runnable task =
        () -> {
          try {
            work.accept(
                new PaulProgress() {
                  @Override
                  public void start(int total) {
                    status.updateAndGet(s -> s.withTotal(total));
                  }

                  @Override
                  public void tick() {
                    status.updateAndGet(PaulJobStatus::incProcessed);
                  }
                });
            status.updateAndGet(s -> s.done(Instant.now()));
          } catch (RuntimeException e) {
            status.updateAndGet(s -> s.failed(e.getMessage(), Instant.now()));
          } finally {
            running.set(false);
          }
        };
    try {
      executor.submit(task);
    } catch (RuntimeException e) {
      // The task was never queued, so its finally-block won't run — release the
      // guard here so a rejected submit (e.g. executor shut down) can't lock Paul out.
      status.updateAndGet(s -> s.failed("Could not start job: " + e.getMessage(), Instant.now()));
      running.set(false);
      throw e;
    }
    return status.get();
  }
}
