package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(FakePaulOracleConfig.class)
@SpringBootTest(
    properties = {
      "app.paul.oracles[0].key=otto",
      "app.paul.oracles[0].google-sub=otto-bot-oracle",
      "app.paul.oracles[0].display-name=Otto la Nutria",
      "app.paul.oracles[0].models[0]=otto:otto-model-x"
    })
class MultiOracleIT extends AbstractIntegrationTest {

  private static final long KO_MATCH = 9301L;

  @Autowired PaulPredictionService predictionService;
  @Autowired PaulEnsembleService ensembleService;
  @Autowired PaulService paulService;
  @Autowired PaulPredictionRepository repo;
  @Autowired JdbcTemplate jdbc;

  @BeforeEach
  void seedOttoAndMatch() {
    jdbc.update(
        "INSERT INTO users (google_sub,email,display_name,role,is_bot) "
            + "VALUES ('otto-bot-oracle','otto@test','Otto','player',true) "
            + "ON CONFLICT (google_sub) DO NOTHING");
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        KO_MATCH);
  }

  @AfterEach
  void cleanup() {
    FakePaulOracleConfig.failModel.set(null);
    FakePaulOracleConfig.forcedResult.set(null);
    jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", KO_MATCH);
    jdbc.update("DELETE FROM match WHERE id = ?", KO_MATCH);
  }

  @Test
  void generateTagsCandidatesPerOracle() {
    predictionService.generateOpen();
    // otto = 1 model; paul (test config) = 2 models.
    assertThat(repo.findByOracleAndMatchIdAndKind("otto", KO_MATCH, PaulPrediction.KIND_CANDIDATE))
        .hasSize(1)
        .allSatisfy(p -> assertThat(p.getModel()).isEqualTo("otto-model-x"));
    assertThat(repo.findByOracleAndMatchIdAndKind("paul", KO_MATCH, PaulPrediction.KIND_CANDIDATE))
        .hasSize(2);
  }

  @Test
  void synthesizePromotesSingleModelCandidateToOfficial() {
    predictionService.generateOpen();
    ensembleService.synthesizeOpen();
    var ottoOfficial =
        repo.findByOracleAndMatchIdAndModelAndKind(
            "otto", KO_MATCH, "ensemble", PaulPrediction.KIND_OFFICIAL);
    assertThat(ottoOfficial).isPresent();
    // FakePaulOracleConfig default result is 2-1, so the promoted official copies that.
    assertThat(ottoOfficial.get().getScoreT1()).isEqualTo(2);
    assertThat(ottoOfficial.get().getScoreT2()).isEqualTo(1);
    assertThat(ottoOfficial.get().getOracle()).isEqualTo("otto");
  }

  @Test
  void revealCreatesBetsForEachOracleQuiniela() {
    predictionService.generateOpen();
    ensembleService.synthesizeOpen();
    paulService.reveal();

    Integer ottoBets =
        jdbc.queryForObject(
            "SELECT count(*) FROM bet b JOIN quiniela q ON q.id=b.quiniela_id "
                + "JOIN users u ON u.id=q.user_id "
                + "WHERE u.google_sub='otto-bot-oracle' AND b.match_id=?",
            Integer.class,
            KO_MATCH);
    assertThat(ottoBets).isEqualTo(1);
  }
}
