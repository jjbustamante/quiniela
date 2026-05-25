package io.quiniela.api.pool;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class PoolRepositoryIT extends AbstractIntegrationTest {

  @Autowired PoolRepository pools;
  @Autowired PrizeSplitRepository splits;

  @Test
  void defaultPoolIsSeeded() {
    var pool = pools.findById(1L).orElseThrow();
    assertThat(pool.getName()).isEqualTo("Quiniela Panas");
    assertThat(pool.getCurrency()).isEqualTo("USD");
    assertThat(pool.getEntryFeeCents()).isEqualTo(2000);
    assertThat(pool.getLockedAt()).isNull();
  }

  @Test
  void seedPrizeSplitSumsTo100() {
    var rows = splits.findByPoolIdOrderByRankAsc(1L);
    assertThat(rows).hasSize(3);
    assertThat(rows.stream().mapToInt(PrizeSplit::getPercentage).sum()).isEqualTo(100);
    assertThat(rows.get(0).getRank()).isEqualTo(1);
    assertThat(rows.get(0).getPercentage()).isEqualTo(80);
  }
}
