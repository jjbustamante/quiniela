package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FlagEmojisTest {

  @Test
  void commonFifaCodesMapToFlagEmojis() {
    assertThat(FlagEmojis.toEmoji("ESP")).isEqualTo("🇪🇸");
    assertThat(FlagEmojis.toEmoji("MEX")).isEqualTo("🇲🇽");
    assertThat(FlagEmojis.toEmoji("ARG")).isEqualTo("🇦🇷");
    assertThat(FlagEmojis.toEmoji("BRA")).isEqualTo("🇧🇷");
    assertThat(FlagEmojis.toEmoji("GER")).isEqualTo("🇩🇪"); // FIFA != ISO alpha-3
    assertThat(FlagEmojis.toEmoji("KOR")).isEqualTo("🇰🇷");
  }

  @Test
  void englandFallsBackToUkFlag() {
    assertThat(FlagEmojis.toEmoji("ENG")).isEqualTo("🇬🇧");
    assertThat(FlagEmojis.toEmoji("WAL")).isEqualTo("🇬🇧");
    assertThat(FlagEmojis.toEmoji("SCO")).isEqualTo("🇬🇧");
  }

  @Test
  void unknownOrNullCodesReturnNull() {
    assertThat(FlagEmojis.toEmoji(null)).isNull();
    assertThat(FlagEmojis.toEmoji("XXX")).isNull();
    assertThat(FlagEmojis.toEmoji("TBD")).isNull();
  }

  @Test
  void caseInsensitive() {
    assertThat(FlagEmojis.toEmoji("esp")).isEqualTo("🇪🇸");
    assertThat(FlagEmojis.toEmoji("Esp")).isEqualTo("🇪🇸");
  }

  @Test
  void alpha2DirectConversionForReference() {
    // Sanity: regional-indicator codepoints render as expected.
    assertThat(FlagEmojis.toFlagFromAlpha2("ES")).isEqualTo("🇪🇸");
    assertThat(FlagEmojis.toFlagFromAlpha2("US")).isEqualTo("🇺🇸");
  }
}
