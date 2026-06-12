package io.quiniela.api.footballdata;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Logs instead of enqueuing. Active whenever no real {@link ResultsTaskQueue} bean exists. */
@Configuration
public class NoopResultsTaskQueue {

  private static final Logger log = LoggerFactory.getLogger(NoopResultsTaskQueue.class);

  @Bean
  @ConditionalOnMissingBean(ResultsTaskQueue.class)
  ResultsTaskQueue noopResultsTaskQueue() {
    return (matchId, when, dedupName) ->
        log.info("[noop queue] would enqueue match {} at {} (name={})", matchId, when, dedupName);
  }
}
