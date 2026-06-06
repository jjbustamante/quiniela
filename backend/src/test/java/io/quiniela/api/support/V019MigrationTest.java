package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V019MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void roundHasPointsMultiplierColumn() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'round' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("points_multiplier");
  }

  @Test
  void groupSeedsToOneAndKnockoutsToTwo() {
    var jdbc = new JdbcTemplate(dataSource);
    Integer group =
        jdbc.queryForObject(
            "SELECT points_multiplier FROM round WHERE code = 'GROUP'", Integer.class);
    assertThat(group).isEqualTo(1);

    var knockoutMultipliers =
        jdbc.queryForList(
            "SELECT points_multiplier FROM round WHERE code <> 'GROUP'", Integer.class);
    assertThat(knockoutMultipliers).isNotEmpty().allMatch(m -> m == 2);
  }
}
