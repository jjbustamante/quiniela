package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PaulPredictionRepositoryIT extends AbstractIntegrationTest {

  @Autowired PaulPredictionRepository repo;

  @Test
  void savesAndQueriesByMatchAndKind() {
    // match id 1 exists from V007 fixtures (first group match).
    var p =
        new PaulPrediction(
            1L,
            "google",
            "gemini-2.5-flash",
            PaulPrediction.KIND_CANDIDATE,
            2,
            1,
            new BigDecimal("0.70"),
            "Paul lo ve claro.",
            "es",
            PaulPrediction.SOURCE_AI);
    repo.save(p);

    var candidates = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(candidates).hasSize(1);
    assertThat(candidates.get(0).getModel()).isEqualTo("gemini-2.5-flash");
    assertThat(candidates.get(0).getScoreT1()).isEqualTo(2);
    assertThat(repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_OFFICIAL)).isEmpty();
  }
}
