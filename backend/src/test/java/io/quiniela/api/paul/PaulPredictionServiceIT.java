package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(FakePaulOracleConfig.class)
class PaulPredictionServiceIT extends AbstractIntegrationTest {

  private static final long KO_MATCH = 9001L;
  private static final long KO_RANK_MATCH = 9200L;
  private static final long RANK_TEAM_1 = 9101L;
  private static final long RANK_TEAM_2 = 9102L;

  @Autowired PaulPredictionService service;
  @Autowired PaulPredictionRepository repo;
  @Autowired JdbcTemplate jdbc;

  @AfterEach
  void reset() {
    FakePaulOracleConfig.failModel.set(null);
    FakePaulOracleConfig.forcedResult.set(null);
    jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", KO_MATCH);
    jdbc.update("DELETE FROM match WHERE id = ?", KO_MATCH);
    jdbc.update("DELETE FROM paul_prediction WHERE match_id = ?", KO_RANK_MATCH);
    jdbc.update("DELETE FROM match WHERE id = ?", KO_RANK_MATCH);
    jdbc.update("DELETE FROM team WHERE id IN (?, ?)", RANK_TEAM_1, RANK_TEAM_2);
  }

  private void insertR32Match() {
    // round 2 = R32; teams 1 (MEX) and 2 (CRC); future kickoff so it is "open".
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, 1, 2, FALSE, now() + interval '2 days')",
        KO_MATCH);
  }

  @Test
  void generatesOneCandidatePerModelPerGroupMatch() {
    int created = service.generateOpen();
    // 72 open group matches × 2 configured models = 144 candidate rows.
    assertThat(created).isEqualTo(144);
    assertThat(repo.findByKind(PaulPrediction.KIND_CANDIDATE)).hasSize(144);
    var forMatch1 = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(forMatch1).hasSize(2);
    assertThat(forMatch1).allMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_AI));
    assertThat(forMatch1).allMatch(p -> p.getPredictedWinnerId() == null); // group → null
  }

  @Test
  void fallsBackToDeterministicStubWhenAModelFails() {
    FakePaulOracleConfig.failModel.set("gemini-2.5-pro");
    service.generateOpen();
    var forMatch1 = repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE);
    assertThat(forMatch1).hasSize(2);
    assertThat(forMatch1).anyMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_FALLBACK));
    assertThat(forMatch1).anyMatch(p -> p.getSource().equals(PaulPrediction.SOURCE_AI));
  }

  @Test
  void regenerationReplacesExistingCandidates() {
    service.generateOpen();
    int second = service.generateOpen();
    assertThat(second).isEqualTo(144);
    assertThat(repo.findByMatchIdAndKind(1L, PaulPrediction.KIND_CANDIDATE)).hasSize(2);
  }

  @Test
  void knockoutDrawSetsPredictedWinnerFromAdvancing() {
    insertR32Match();
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(1, 1, 0.5, "empate, avanza local", "LOCAL"));
    service.generateOpen();
    var ko = repo.findByMatchIdAndKind(KO_MATCH, PaulPrediction.KIND_CANDIDATE);
    assertThat(ko).hasSize(2);
    assertThat(ko).allMatch(p -> p.getScoreT1() == 1 && p.getScoreT2() == 1);
    assertThat(ko)
        .allMatch(p -> p.getPredictedWinnerId() != null && p.getPredictedWinnerId() == 1L);
  }

  @Test
  void knockoutDecisiveLeavesPredictedWinnerNull() {
    insertR32Match();
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(2, 1, 0.7, "gana el local", "LOCAL"));
    service.generateOpen();
    var ko = repo.findByMatchIdAndKind(KO_MATCH, PaulPrediction.KIND_CANDIDATE);
    assertThat(ko).allMatch(p -> p.getPredictedWinnerId() == null); // decisive → null
  }

  @Test
  void knockoutDrawWithoutAdvancingFallsBackToTeam1() {
    insertR32Match();
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(0, 0, 0.5, "empate sin pick", null));
    service.generateOpen();
    var ko = repo.findByMatchIdAndKind(KO_MATCH, PaulPrediction.KIND_CANDIDATE);
    // Seeded test teams have NULL fifa_ranking → deterministic fallback = team1 (id 1).
    assertThat(ko)
        .allMatch(p -> p.getPredictedWinnerId() != null && p.getPredictedWinnerId() == 1L);
  }

  @Test
  void knockoutDrawWithoutAdvancingSelectsBetterFifaRankedTeam() {
    // RANK_TEAM_1 is the worse-ranked team (higher FIFA number = lower rank).
    // RANK_TEAM_2 is the better-ranked team (lower FIFA number = higher rank).
    // With team1 = worse rank and team2 = better rank, the ranking branch must return team2Id.
    jdbc.update(
        "INSERT INTO team (id, tournament_id, code, name, group_code, flag_emoji, fifa_ranking)"
            + " VALUES (?, 1, 'T91', 'Test Ranked 1', NULL, NULL, 50)",
        RANK_TEAM_1);
    jdbc.update(
        "INSERT INTO team (id, tournament_id, code, name, group_code, flag_emoji, fifa_ranking)"
            + " VALUES (?, 1, 'T92', 'Test Ranked 2', NULL, NULL, 10)",
        RANK_TEAM_2);
    jdbc.update(
        "INSERT INTO match (id, tournament_id, round_id, team_1_id, team_2_id, played, kickoff_at)"
            + " VALUES (?, 1, 2, ?, ?, FALSE, now() + interval '2 days')",
        KO_RANK_MATCH,
        RANK_TEAM_1,
        RANK_TEAM_2);
    FakePaulOracleConfig.forcedResult.set(
        new PaulPredictionResult(0, 0, 0.5, "empate sin pick", null));
    service.generateOpen();
    var ko = repo.findByMatchIdAndKind(KO_RANK_MATCH, PaulPrediction.KIND_CANDIDATE);
    // fifa_ranking=10 (RANK_TEAM_2) < fifa_ranking=50 (RANK_TEAM_1) → team2 advances.
    assertThat(ko).hasSize(2);
    assertThat(ko).allSatisfy(p -> assertThat(p.getPredictedWinnerId()).isEqualTo(RANK_TEAM_2));
  }
}
