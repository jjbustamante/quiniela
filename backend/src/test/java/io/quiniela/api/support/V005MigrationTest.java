package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V005MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void quinielaTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'quiniela' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("id", "pool_id", "user_id", "points");
  }

  @Test
  void betTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'bet' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("quiniela_id", "match_id", "score_t1", "score_t2");
  }

  @Test
  void scoringTriggerExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var count =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_trigger "
                + "WHERE tgname = 'matches_score_update_trigger' AND NOT tgisinternal",
            Long.class);
    assertThat(count).isEqualTo(1L);
  }
}
