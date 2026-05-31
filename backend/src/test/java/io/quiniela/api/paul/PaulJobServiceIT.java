package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ResponseStatusException;

/**
 * Verifies the one-job-at-a-time guard. Uses a deferring executor that captures the job task
 * without running it, so the first job stays RUNNING while we assert a second start is rejected.
 */
@Import({FakePaulOracleConfig.class, PaulJobServiceIT.DeferringExecutorConfig.class})
class PaulJobServiceIT extends AbstractIntegrationTest {

  @Autowired PaulJobService jobService;
  @Autowired DeferringExecutor executor;

  @Test
  void rejectsConcurrentJobsThenRecovers() {
    PaulJobStatus started = jobService.startGenerate();
    assertThat(started.state()).isEqualTo(PaulJobStatus.STATE_RUNNING);
    assertThat(started.phase()).isEqualTo("generate");

    // A second job while the first is still running is rejected (409).
    assertThatThrownBy(jobService::startSynthesize)
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("already running");

    // Let the captured generate task actually run to completion.
    executor.runCaptured();
    assertThat(jobService.current().state()).isEqualTo(PaulJobStatus.STATE_DONE);
    assertThat(jobService.current().processed()).isEqualTo(144);

    // With the first job done, a new job can start.
    PaulJobStatus next = jobService.startSynthesize();
    assertThat(next.phase()).isEqualTo("synthesize");
  }

  static final class DeferringExecutor implements PaulJobExecutor {
    private Runnable captured;

    @Override
    public void submit(Runnable task) {
      this.captured = task;
    }

    void runCaptured() {
      captured.run();
    }
  }

  @TestConfiguration
  static class DeferringExecutorConfig {
    @Bean
    @Primary
    DeferringExecutor deferringExecutor() {
      return new DeferringExecutor();
    }
  }
}
