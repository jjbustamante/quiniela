package io.quiniela.api.paul;

/**
 * Indirection over where a Paul batch job runs. Production submits to a background daemon thread so
 * the HTTP request can return 202 immediately; tests provide a synchronous implementation so job
 * completion is deterministic. A custom type (rather than {@link java.util.concurrent.Executor})
 * keeps injection unambiguous and lets tests override it with {@code @Primary}.
 */
public interface PaulJobExecutor {

  void submit(Runnable task);
}
