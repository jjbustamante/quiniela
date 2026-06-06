package io.quiniela.api.compare;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Pins {@link CompareService#scoreMatchForBet} (the Java mirror used by the Duelos / head-to-head
 * view) against the live Postgres {@code score_match_for_bet} function that computes the real,
 * stored leaderboard points.
 *
 * <p>WHY THIS EXISTS: the scoring rules are duplicated across Java and a PL/pgSQL function. They
 * MUST agree, or the points shown in the H2H preview silently differ from a player's actual
 * standings. They drifted once already — the Java mirror was left at the V005 ladder (5/3/2) while
 * the DB advanced to the V010 additive model — which this test now guards against permanently.
 *
 * <p>SCOPE: the H2H view calls the scorer with no predicted/advanced winner ids, so this pins the
 * DB function invoked with NULL winner args (its defaults). V016's knockout-regulation-draw
 * refinement (predicted_winner_id vs advanced_team_id) is a DB-only path the preview does not
 * model; consolidating it is tracked in BACKLOG.md.
 */
class ScoringDivergenceTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  /**
   * Exhaustively compares both implementations across every 0–5 scoreline (1,296 combinations) for
   * both group and knockout matches, in a single DB round-trip via {@code generate_series}.
   */
  @Test
  void javaMirrorMatchesDbScoringFunctionForEveryScoreline() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    List<String> mismatches = new ArrayList<>();
    jdbc.query(
        """
        SELECT ko, b1, b2, a1, a2,
               score_match_for_bet(ko, b1, b2, a1, a2) AS pts
        FROM generate_series(0, 5) AS b1,
             generate_series(0, 5) AS b2,
             generate_series(0, 5) AS a1,
             generate_series(0, 5) AS a2,
             (VALUES (false), (true)) AS k(ko)
        """,
        rs -> {
          boolean ko = rs.getBoolean("ko");
          int b1 = rs.getInt("b1");
          int b2 = rs.getInt("b2");
          int a1 = rs.getInt("a1");
          int a2 = rs.getInt("a2");
          int db = rs.getInt("pts");
          // DB 5-arg call defaults multiplier to COALESCE(NULL, ko?2:1); mirror that here.
          int java = CompareService.scoreMatchForBet(ko ? 2 : 1, b1, b2, a1, a2);
          if (db != java) {
            mismatches.add(
                String.format(
                    "ko=%s bet=%d-%d actual=%d-%d -> java=%d db=%d", ko, b1, b2, a1, a2, java, db));
          }
        });

    assertThat(mismatches)
        .as(
            "Java CompareService.scoreMatchForBet must match the DB score_match_for_bet function "
                + "for every scoreline; divergences:\n%s",
            String.join("\n", mismatches))
        .isEmpty();
  }

  /**
   * Pins Java and DB across explicit multipliers (1..4) for every scoreline, passing the multiplier
   * to the DB function directly so the two scale identically.
   */
  @Test
  void javaMirrorMatchesDbScoringAcrossMultipliers() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    List<String> mismatches = new ArrayList<>();
    jdbc.query(
        """
        SELECT mult, b1, b2, a1, a2,
               score_match_for_bet(true, b1, b2, a1, a2, NULL, NULL, mult) AS pts
        FROM generate_series(0, 5) AS b1,
             generate_series(0, 5) AS b2,
             generate_series(0, 5) AS a1,
             generate_series(0, 5) AS a2,
             generate_series(1, 4) AS mult
        """,
        rs -> {
          int mult = rs.getInt("mult");
          int b1 = rs.getInt("b1");
          int b2 = rs.getInt("b2");
          int a1 = rs.getInt("a1");
          int a2 = rs.getInt("a2");
          int db = rs.getInt("pts");
          int java = CompareService.scoreMatchForBet(mult, b1, b2, a1, a2);
          if (db != java) {
            mismatches.add(
                String.format(
                    "mult=%d bet=%d-%d actual=%d-%d -> java=%d db=%d",
                    mult, b1, b2, a1, a2, java, db));
          }
        });

    assertThat(mismatches)
        .as(
            "Java and DB scoring must agree across multipliers; divergences:\n%s",
            String.join("\n", mismatches))
        .isEmpty();
  }

  /** A match with no result yet scores zero in both implementations. */
  @Test
  void unplayedMatchScoresZeroInBothImpls() {
    JdbcTemplate jdbc = new JdbcTemplate(dataSource);

    assertThat(CompareService.scoreMatchForBet(1, 1, 0, null, null)).isZero();
    assertThat(CompareService.scoreMatchForBet(2, 1, 0, null, null)).isZero();

    Integer dbGroup =
        jdbc.queryForObject(
            "SELECT score_match_for_bet(?, ?, ?, ?::int, ?::int)",
            Integer.class,
            false,
            1,
            0,
            null,
            null);
    assertThat(dbGroup).isZero();
  }
}
