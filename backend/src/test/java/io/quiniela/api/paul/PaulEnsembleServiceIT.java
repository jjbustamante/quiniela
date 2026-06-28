package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(FakePaulOracleConfig.class)
class PaulEnsembleServiceIT extends AbstractIntegrationTest {

  private static final long KO_MATCH = 9011L;

  @Autowired PaulPredictionService predictionService;
  @Autowired PaulEnsembleService ensembleService;
  @Autowired PaulPredictionRepository repo;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void cleanupKo() {
    FakePaulOracleConfig.forcedResult.set(null);
    jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", KO_MATCH);
    jdbc.update("DELETE FROM match WHERE id = ?", KO_MATCH);
  }

  @Test
  void synthesizesOneOfficialPerMatchWithCandidates() {
    predictionService.generateOpen(); // 144 candidates over 72 matches
    int officials = ensembleService.synthesizeOpen();
    assertThat(officials).isEqualTo(72);

    var official = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_OFFICIAL);
    assertThat(official).hasSize(1);
    assertThat(official.get(0).getModel()).isEqualTo("ensemble");
    assertThat(official.get(0).getReasoning()).isNotBlank();
  }

  @Test
  void skipsMatchesWithoutCandidates() {
    // No candidates generated → nothing to synthesize.
    assertThat(ensembleService.synthesizeOpen()).isZero();
    assertThat(repo.findByKind(PaulPrediction.KIND_OFFICIAL)).isEmpty();
  }

  @Test
  void synthesizesKnockoutOfficialWithAdvancing() {
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        KO_MATCH);
    // Two candidates, both a 1-1 draw with the LOCAL (team 1) advancing.
    for (String model : new String[] {"gemini-2.5-pro", "gemini-2.5-flash"}) {
      repo.save(
          new PaulPrediction(
              KO_MATCH,
              "google",
              model,
              PaulPrediction.KIND_CANDIDATE,
              1,
              1,
              null,
              "empate",
              "es",
              PaulPrediction.SOURCE_AI,
              1L));
    }
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(1, 1, 0.6, "empate, avanza local", "LOCAL"));

    boolean did = ensembleService.synthesizeForMatch(KO_MATCH);

    assertThat(did).isTrue();
    var official =
        repo.findByMatchIdAndModelAndKind(KO_MATCH, "ensemble", PaulPrediction.KIND_OFFICIAL);
    assertThat(official).isPresent();
    assertThat(official.get().getScoreT1()).isEqualTo(1);
    assertThat(official.get().getPredictedWinnerId()).isEqualTo(1L);
  }
}
