package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PaulPropertiesTest {

  @Test
  void allOraclesStartsWithPaulBuiltFromBaseFields() {
    PaulProperties p =
        new PaulProperties(
            "google",
            List.of("gemini-2.5-pro", "gemini-2.5-flash"),
            "gemini-2.5-pro",
            "proj",
            "us-central1",
            List.of());
    List<Oracle> oracles = p.allOracles();
    assertThat(oracles).hasSize(1);
    Oracle paul = oracles.get(0);
    assertThat(paul.key()).isEqualTo("paul");
    assertThat(paul.googleSub()).isEqualTo("paul-bot-oracle");
    assertThat(paul.roster()).hasSize(2);
    assertThat(paul.isEnsemble()).isTrue();
    assertThat(paul.ensembleSpec().model()).isEqualTo("gemini-2.5-pro");
  }

  @Test
  void singleModelExtraOracleHasNoEnsemble() {
    PaulProperties.OracleSpec otto =
        new PaulProperties.OracleSpec(
            "otto",
            "otto-bot-oracle",
            "Otto la Nutria 🦦",
            List.of("deepseek:deepseek-ai/deepseek-v3.1"),
            null);
    PaulProperties p =
        new PaulProperties(
            "google",
            List.of("gemini-2.5-pro"),
            "gemini-2.5-pro",
            "proj",
            "us-central1",
            List.of(otto));
    List<Oracle> oracles = p.allOracles();
    assertThat(oracles).hasSize(2);
    Oracle o = oracles.get(1);
    assertThat(o.key()).isEqualTo("otto");
    assertThat(o.roster()).hasSize(1);
    assertThat(o.roster().get(0).provider()).isEqualTo("deepseek");
    assertThat(o.roster().get(0).model()).isEqualTo("deepseek-ai/deepseek-v3.1");
    assertThat(o.isEnsemble()).isFalse();
    assertThat(o.ensembleSpec()).isNull();
  }
}
