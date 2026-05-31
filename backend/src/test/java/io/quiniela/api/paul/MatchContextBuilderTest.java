package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchContextBuilderTest {

  private final MatchContextBuilder builder = new MatchContextBuilder();

  @Test
  void buildsUserPromptWithTeamsRankingsAndGroup() {
    String prompt = builder.userPrompt("GROUP", "A", "México", "MEX", 16, "Costa Rica", "CRC", 29);
    assertThat(prompt)
        .contains("México")
        .contains("Costa Rica")
        .contains("Grupo A")
        .contains("16")
        .contains("29");
  }

  @Test
  void omitsRankingWhenNull() {
    String prompt =
        builder.userPrompt("GROUP", "K", "Países K1", "TBD_K1", null, "Países K2", "TBD_K2", null);
    assertThat(prompt).contains("Países K1").doesNotContain("ranking FIFA:");
  }
}
