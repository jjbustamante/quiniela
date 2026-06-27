package io.quiniela.api.footballdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SyncPropertiesTest {

  @Test
  void appliesDefaultsWhenNullsGiven() {
    SyncProperties p = new SyncProperties(null, null, null, null, null, null, null);
    assertThat(p.firstPollOffsetMinutes()).isEqualTo(0);
    assertThat(p.pollWindowHours()).isEqualTo(5);
    assertThat(p.retryIntervalMinutes()).isEqualTo(5);
    assertThat(p.tailRefreshIntervalMinutes()).isEqualTo(30);
    assertThat(p.tailWindowHours()).isEqualTo(3);
    assertThat(p.tasks().enabled()).isFalse();
  }
}
