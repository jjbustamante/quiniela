package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FootballDataLoaderTest {

  @Test
  void mapGroupNameHandlesSpaceAndUnderscoreForms() {
    assertThat(FootballDataLoader.mapGroupName("Group A")).isEqualTo("A");
    assertThat(FootballDataLoader.mapGroupName("Group L")).isEqualTo("L");
    assertThat(FootballDataLoader.mapGroupName("GROUP_F")).isEqualTo("F");
    assertThat(FootballDataLoader.mapGroupName(null)).isNull();
    assertThat(FootballDataLoader.mapGroupName("Knockout")).isNull();
  }

  @Test
  void mapStageToRoundCodeCoversAllSupportedStages() {
    assertThat(FootballDataLoader.mapStageToRoundCode("GROUP_STAGE")).isEqualTo("GROUP");
    assertThat(FootballDataLoader.mapStageToRoundCode("ROUND_OF_32")).isEqualTo("R32");
    assertThat(FootballDataLoader.mapStageToRoundCode("LAST_16")).isEqualTo("R16");
    assertThat(FootballDataLoader.mapStageToRoundCode("QUARTER_FINALS")).isEqualTo("QF");
    assertThat(FootballDataLoader.mapStageToRoundCode("SEMI_FINALS")).isEqualTo("SF");
    assertThat(FootballDataLoader.mapStageToRoundCode("THIRD_PLACE")).isEqualTo("THIRD_PLACE");
    assertThat(FootballDataLoader.mapStageToRoundCode("FINAL")).isEqualTo("FINAL");
    assertThat(FootballDataLoader.mapStageToRoundCode("UNKNOWN_STAGE")).isNull();
    assertThat(FootballDataLoader.mapStageToRoundCode(null)).isNull();
  }
}
