package io.quiniela.api.invite;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class InvitePathGeneratorTest {

  InvitePathGenerator gen = new InvitePathGenerator();

  @Test
  void buildsSlugFromDisplayName() {
    String path = gen.generate("Juan Bustamante");
    assertThat(path).startsWith("juan-bustamante-");
    assertThat(path).matches("juan-bustamante-[a-z0-9]{6}");
  }

  @Test
  void stripsAccentsAndNonAlphanumeric() {
    assertThat(gen.generate("José Núñez")).startsWith("jose-nunez-");
    assertThat(gen.generate("Carla O'Brien")).startsWith("carla-obrien-");
  }

  @Test
  void fallsBackToUserWhenNameIsBlank() {
    assertThat(gen.generate("")).startsWith("user-");
    assertThat(gen.generate(null)).startsWith("user-");
  }

  @Test
  void capsSlugLengthAt32CharsBeforeSuffix() {
    String veryLong = "abcdefghijklmnopqrstuvwxyz0123456789ABC";
    String path = gen.generate(veryLong);
    assertThat(path.split("-")[0].length()).isLessThanOrEqualTo(32);
  }
}
