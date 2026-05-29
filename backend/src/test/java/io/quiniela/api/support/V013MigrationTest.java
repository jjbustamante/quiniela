package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V013MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void tournamentHasTestModeColumnDefaultingTrue() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'tournament' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("test_mode");

    Boolean tm =
        jdbc.queryForObject("SELECT test_mode FROM tournament WHERE id = 1", Boolean.class);
    assertThat(tm).isTrue();
  }
}
