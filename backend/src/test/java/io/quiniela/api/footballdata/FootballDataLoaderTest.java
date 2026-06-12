package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FootballDataLoaderTest {

  @Test
  void mapGroupNameHandlesSpaceAndUnderscoreForms() {
    assertThat(FootballDataSyncService.mapGroupName("Group A")).isEqualTo("A");
    assertThat(FootballDataSyncService.mapGroupName("Group L")).isEqualTo("L");
    assertThat(FootballDataSyncService.mapGroupName("GROUP_F")).isEqualTo("F");
    assertThat(FootballDataSyncService.mapGroupName(null)).isNull();
    assertThat(FootballDataSyncService.mapGroupName("Knockout")).isNull();
  }

  @Test
  void mapStageToRoundCodeCoversAllSupportedStages() {
    assertThat(FootballDataSyncService.mapStageToRoundCode("GROUP_STAGE")).isEqualTo("GROUP");
    assertThat(FootballDataSyncService.mapStageToRoundCode("ROUND_OF_32")).isEqualTo("R32");
    assertThat(FootballDataSyncService.mapStageToRoundCode("LAST_16")).isEqualTo("R16");
    assertThat(FootballDataSyncService.mapStageToRoundCode("QUARTER_FINALS")).isEqualTo("QF");
    assertThat(FootballDataSyncService.mapStageToRoundCode("SEMI_FINALS")).isEqualTo("SF");
    assertThat(FootballDataSyncService.mapStageToRoundCode("THIRD_PLACE")).isEqualTo("THIRD_PLACE");
    assertThat(FootballDataSyncService.mapStageToRoundCode("FINAL")).isEqualTo("FINAL");
    assertThat(FootballDataSyncService.mapStageToRoundCode("UNKNOWN_STAGE")).isNull();
    assertThat(FootballDataSyncService.mapStageToRoundCode(null)).isNull();
  }

  @org.junit.jupiter.api.Test
  void advancingTeamIdReadsPenaltyWinnerFromWinnerField() {
    var home = new FootballDataClient.MatchTeam(1001L, "Home");
    var away = new FootballDataClient.MatchTeam(1002L, "Away");
    var drawScore =
        new FootballDataClient.MatchScore(
            "AWAY_TEAM",
            "PENALTY_SHOOTOUT",
            new FootballDataClient.MatchScoreFull(1, 1),
            new FootballDataClient.MatchScoreFull(3, 5));
    var m =
        new FootballDataClient.MatchApi(
            7001L, "2026-07-01T18:00:00Z", "FINISHED", "LAST_32", null, home, away, drawScore);

    org.junit.jupiter.api.Assertions.assertEquals(
        1002L, FootballDataSyncService.advancingTeamId(m));
  }

  @org.junit.jupiter.api.Test
  void advancingTeamIdIsNullForGroupDraw() {
    var home = new FootballDataClient.MatchTeam(1001L, "Home");
    var away = new FootballDataClient.MatchTeam(1002L, "Away");
    var drawScore =
        new FootballDataClient.MatchScore(
            "DRAW", "REGULAR", new FootballDataClient.MatchScoreFull(1, 1), null);
    var m =
        new FootballDataClient.MatchApi(
            8001L,
            "2026-06-15T18:00:00Z",
            "FINISHED",
            "GROUP_STAGE",
            "Group A",
            home,
            away,
            drawScore);

    org.junit.jupiter.api.Assertions.assertNull(FootballDataSyncService.advancingTeamId(m));
  }
}
