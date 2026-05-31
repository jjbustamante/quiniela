package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(FakePaulOracleConfig.class)
class PaulPredictionServiceIT extends AbstractIntegrationTest {

  @Autowired PaulPredictionService service;
  @Autowired PaulPredictionRepository repo;

  @AfterEach
  void reset() {
    FakePaulOracleConfig.failModel.set(null);
  }

  @Test
  void generatesOneCandidatePerModelPerGroupMatch() {
    int created = service.generateAllGroup();
    // 72 group matches × 2 configured models = 144 candidate rows.
    assertThat(created).isEqualTo(144);
    assertThat(repo.findByKind(PaulPrediction.KIND_CANDIDATE)).hasSize(144);
    var forMatch1 = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(forMatch1).hasSize(2);
    assertThat(forMatch1).allMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_AI));
  }

  @Test
  void fallsBackToDeterministicStubWhenAModelFails() {
    FakePaulOracleConfig.failModel.set("gemini-2.5-pro");
    service.generateAllGroup();
    var forMatch1 = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(forMatch1).hasSize(2);
    assertThat(forMatch1).anyMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_FALLBACK));
    assertThat(forMatch1).anyMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_AI));
  }

  @Test
  void regenerationReplacesExistingCandidates() {
    service.generateAllGroup();
    int second = service.generateAllGroup();
    assertThat(second).isEqualTo(144);
    assertThat(repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE)).hasSize(2);
  }
}
