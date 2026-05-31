package io.quiniela.api.paul;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaulExecutorConfig {

  /**
   * Single background daemon thread. Single-threaded so two batch jobs can never run concurrently
   * (the {@link PaulJobService} guard is the primary defense; this is belt-and-braces). Requires
   * Cloud Run CPU to stay allocated after the response is sent — see {@code cpu_idle = false} on
   * the api service in {@code iac/cloud_run.tf}.
   */
  @Bean
  PaulJobExecutor paulJobExecutor() {
    ExecutorService pool =
        Executors.newSingleThreadExecutor(
            r -> {
              Thread t = new Thread(r, "paul-job");
              t.setDaemon(true);
              return t;
            });
    return pool::execute;
  }
}
