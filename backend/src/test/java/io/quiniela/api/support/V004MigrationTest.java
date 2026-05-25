package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V004MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void teamTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'team' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("id", "tournament_id", "code", "name", "group_code");
  }

  @Test
  void roundTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'round' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("id", "tournament_id", "code", "name", "sequence");
  }

  @Test
  void matchTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'match' AND table_schema = 'public'",
            String.class);
    assertThat(columns)
        .contains(
            "id",
            "tournament_id",
            "round_id",
            "team_1_id",
            "team_2_id",
            "score_t1",
            "score_t2",
            "winner_id",
            "played",
            "kickoff_at",
            "match_parent_1_id",
            "match_parent_2_id");
  }
}
