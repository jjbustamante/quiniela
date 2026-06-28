package io.quiniela.api.paul;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FakePaulOracleConfig {

  /** Set to a model name that should THROW (to exercise the fallback path); null = never throw. */
  public static final AtomicReference<String> failModel = new AtomicReference<>(null);

  /** When non-null, returned for every prediction (lets a test force a draw + advancing). */
  public static final AtomicReference<PaulPredictionResult> forcedResult =
      new AtomicReference<>(null);

  @Bean
  @Primary
  PaulOracle fakePaulOracle() {
    return (system, user, provider, model) -> {
      if (model.equals(failModel.get())) {
        throw new RuntimeException("simulated model failure: " + model);
      }
      PaulPredictionResult forced = forcedResult.get();
      if (forced != null) return forced;
      return new PaulPredictionResult(
          2, 1, 0.66, "Paul lo siente en los tentáculos. [" + model + "]", null);
    };
  }
}
