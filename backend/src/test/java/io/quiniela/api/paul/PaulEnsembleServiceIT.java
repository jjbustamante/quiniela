package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(FakePaulOracleConfig.class)
class PaulEnsembleServiceIT extends AbstractIntegrationTest {

  @Autowired PaulPredictionService predictionService;
  @Autowired PaulEnsembleService ensembleService;
  @Autowired PaulPredictionRepository repo;

  @Test
  void synthesizesOneOfficialPerMatchWithCandidates() {
    predictionService.generateAllGroup(); // 144 candidates over 72 matches
    int officials = ensembleService.synthesizeAllGroup();
    assertThat(officials).isEqualTo(72);

    var official = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_OFFICIAL);
    assertThat(official).hasSize(1);
    assertThat(official.get(0).getModel()).isEqualTo("ensemble");
    assertThat(official.get(0).getReasoning()).isNotBlank();
  }

  @Test
  void skipsMatchesWithoutCandidates() {
    // No candidates generated → nothing to synthesize.
    assertThat(ensembleService.synthesizeAllGroup()).isZero();
    assertThat(repo.findByKind(PaulPrediction.KIND_OFFICIAL)).isEmpty();
  }
}
