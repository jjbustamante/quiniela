package io.quiniela.api.paul;

/**
 * Progress sink for long-running Paul batch jobs. The prediction/ensemble services report against
 * it as they work; the job tracker implements it to update the live status. Decouples the batch
 * services from any knowledge of job state.
 */
public interface PaulProgress {

  /** Called once at the start with the total number of items the job will process. */
  void start(int total);

  /** Called once after each item completes. */
  void tick();

  /** No-op sink — lets the batch methods be called directly (e.g. in tests) without a job. */
  PaulProgress NOOP =
      new PaulProgress() {
        @Override
        public void start(int total) {}

        @Override
        public void tick() {}
      };
}
