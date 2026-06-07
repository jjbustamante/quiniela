package io.quiniela.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pins {@link ScoreBreakdown#of}'s total against the live Postgres {@code score_match_for_bet}
 * across every 0–5 scoreline and multipliers 1–3 (group + knockout), plus explicit
 * knockout-regulation-draw cases. Guarantees a player's per-component breakdown always sums to
 * their real, stored points.
 */
class ScoreBreakdownDivergenceIT extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void breakdownTotalMatchesDbAcrossScorelinesAndMultipliers() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    List<String> mismatches = new ArrayList<>();
    jdbc.query(
        """
        SELECT ko, b1, b2, a1, a2, mult,
               score_match_for_bet(ko, b1, b2, a1, a2, NULL, NULL, mult) AS pts
        FROM generate_series(0, 5) AS b1,
             generate_series(0, 5) AS b2,
             generate_series(0, 5) AS a1,
             generate_series(0, 5) AS a2,
             generate_series(1, 3) AS mult,
             (VALUES (false), (true)) AS k(ko)
        """,
        rs -> {
          boolean ko = rs.getBoolean("ko");
          int b1 = rs.getInt("b1");
          int b2 = rs.getInt("b2");
          int a1 = rs.getInt("a1");
          int a2 = rs.getInt("a2");
          int mult = rs.getInt("mult");
          int db = rs.getInt("pts");
          int java = ScoreBreakdown.of(ko, b1, b2, a1, a2, null, null, mult).total();
          if (db != java) {
            mismatches.add(
                String.format(
                    "ko=%s bet=%d-%d actual=%d-%d mult=%d -> java=%d db=%d",
                    ko, b1, b2, a1, a2, mult, java, db));
          }
        });
    assertThat(mismatches)
        .as(
            "ScoreBreakdown total must match the DB function; divergences:\n%s",
            String.join("\n", mismatches))
        .isEmpty();
  }

  @Test
  void knockoutRegulationDrawMatchesDb() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);
    Integer dbMatch =
        jdbc.queryForObject("SELECT score_match_for_bet(true, 1, 1, 0, 0, 5, 5, 2)", Integer.class);
    assertThat(dbMatch).isEqualTo(ScoreBreakdown.of(true, 1, 1, 0, 0, 5L, 5L, 2).total());
    Integer dbWrong =
        jdbc.queryForObject("SELECT score_match_for_bet(true, 1, 1, 0, 0, 5, 7, 2)", Integer.class);
    assertThat(dbWrong).isEqualTo(ScoreBreakdown.of(true, 1, 1, 0, 0, 5L, 7L, 2).total());
  }
}
