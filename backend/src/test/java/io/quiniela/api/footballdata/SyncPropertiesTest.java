package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SyncPropertiesTest {

  @Test
  void appliesDefaultsWhenNullsGiven() {
    SyncProperties p = new SyncProperties(null, null, null, null, null);
    assertThat(p.matchMinDurationMinutes()).isEqualTo(105);
    assertThat(p.pollWindowHours()).isEqualTo(5);
    assertThat(p.retryIntervalMinutes()).isEqualTo(15);
    assertThat(p.tasks().enabled()).isFalse();
  }
}
