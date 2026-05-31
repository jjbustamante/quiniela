package io.quiniela.api.paul;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Runs Paul batch jobs synchronously (caller thread) so tests are deterministic: by the time a
 * generate/synthesize POST returns, the job has finished and the status endpoint reports DONE.
 */
@TestConfiguration
public class SynchronousPaulExecutorConfig {

  @Bean
  @Primary
  PaulJobExecutor synchronousPaulJobExecutor() {
    return Runnable::run;
  }
}
