package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

  @Test
  void rejectsAZeroMultiplier() {
    var jdbc = new JdbcTemplate(dataSource);
    assertThatThrownBy(
            () -> jdbc.update("UPDATE round SET points_multiplier = 0 WHERE code = 'GROUP'"))
        .hasMessageContaining("points_multiplier");
  }

  @Test
  void sevenArgCallStillScoresKnockoutTimesTwo() {
    // Backward-compat: callers that omit the multiplier arg get the historical
    // knockout ×2 via the COALESCE fallback. bet 2-1, actual 2-1 knockout = (3+2+2)*2 = 14.
    var jdbc = new JdbcTemplate(dataSource);
    Integer pts =
        jdbc.queryForObject(
            "SELECT score_match_for_bet(true, 2, 1, 2, 1, NULL, NULL)", Integer.class);
    assertThat(pts).isEqualTo(14);
  }
}
