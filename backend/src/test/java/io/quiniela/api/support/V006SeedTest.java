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

  @Test
  void fortyEightTeamsSeeded() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM team WHERE tournament_id = 1", Long.class);
    assertThat(n).isEqualTo(48L);
  }

  @Test
  void twelveGroupsOfFour() {
    var jdbc = new JdbcTemplate(dataSource);
    var rows =
        jdbc.queryForList(
            "SELECT group_code, COUNT(*) AS n FROM team "
                + "WHERE tournament_id = 1 AND group_code IS NOT NULL "
                + "GROUP BY group_code ORDER BY group_code");
    assertThat(rows).hasSize(12);
    rows.forEach(r -> assertThat(r.get("n")).isEqualTo(4L));
  }

  @Test
  void seventyTwoGroupMatches() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id = m.round_id "
                + "WHERE m.tournament_id = 1 AND r.code = 'GROUP'",
            Long.class);
    assertThat(n).isEqualTo(72L);
  }

  @Test
  void thirtyTwoKnockoutMatches() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM match m JOIN round r ON r.id = m.round_id "
                + "WHERE m.tournament_id = 1 AND r.code <> 'GROUP'",
            Long.class);
    assertThat(n).isEqualTo(32L);
  }

  @Test
  void totalMatchesIs104() {
    var jdbc = new JdbcTemplate(dataSource);
    Long n = jdbc.queryForObject("SELECT COUNT(*) FROM match WHERE tournament_id = 1", Long.class);
    assertThat(n).isEqualTo(104L);
  }
}
