package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V006SeedTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void sevenRoundsSeeded() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM round WHERE tournament_id = 1", Long.class);
    assertThat(n).isEqualTo(7L);
  }
}
