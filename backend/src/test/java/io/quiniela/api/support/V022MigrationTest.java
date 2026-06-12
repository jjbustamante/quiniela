package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V022 migration: bet.points frozen column + trigger writes it (pre-kickoff bets only).
 *
 * <p>Verifies: (a) bet.points column exists; (b) a bet placed BEFORE kickoff gets bet.points =
 * computed score, and quiniela.points matches; (c) a bet placed AFTER kickoff_at stays 0 and is
 * excluded from quiniela.points — on first score AND on re-score (landmine de-armed); (d)
 * SUM(bet.points over played) = quiniela.points invariant.
 */
class V022MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  private JdbcTemplate jdbc;

  // IDs for the seeded match and quinielas — refreshed before each test.
  private Long matchId;
  private Long beforeQuinielaId;
  private Long afterQuinielaId;

  // Delete the seeded match (cascades its bets) so it doesn't pollute the shared
  // Testcontainers singleton — other ITs assert on the seeded 104-match count.
  @AfterEach
  void cleanupSeededMatch() {
    if (jdbc != null && matchId != null) {
      jdbc.update("DELETE FROM bet WHERE match_id = ?", matchId);
      jdbc.update("DELETE FROM match WHERE id = ?", matchId);
    }
  }

  @BeforeEach
  void seedMatchAndBets() {
    jdbc = new JdbcTemplate(dataSource);

    // Use an existing seeded GROUP round (round_id=1, code='GROUP') and two existing teams.
    // Pick IDs that are safely out of the seeded fixture range so deletes don't leave orphans.
    // We insert a fresh match with a known kickoff_at in the recent past.
    Long roundId =
        jdbc.queryForObject("SELECT id FROM round WHERE code = 'GROUP' LIMIT 1", Long.class);
    Long team1Id =
        jdbc.queryForObject(
            "SELECT id FROM team WHERE tournament_id = 1 ORDER BY id LIMIT 1", Long.class);
    Long team2Id =
        jdbc.queryForObject(
            "SELECT id FROM team WHERE tournament_id = 1 ORDER BY id OFFSET 1 LIMIT 1", Long.class);

    // Insert a test match with kickoff 2 hours ago, NOT played yet.
    matchId =
        jdbc.queryForObject(
            "INSERT INTO match (tournament_id, round_id, group_code, team_1_id, team_2_id,"
                + " kickoff_at, played)"
                + " VALUES (1, ?, 'A', ?, ?, NOW() - INTERVAL '2 hours', FALSE)"
                + " RETURNING id",
            Long.class,
            roundId,
            team1Id,
            team2Id);

    // Create two users (unique google_sub per run).
    Long beforeUserId =
        jdbc.queryForObject(
            "INSERT INTO users (google_sub, email, display_name, role)"
                + " VALUES (?, 'before@v021.test', 'Before Bettor', 'player') RETURNING id",
            Long.class,
            "v021-before-" + System.nanoTime());

    Long afterUserId =
        jdbc.queryForObject(
            "INSERT INTO users (google_sub, email, display_name, role)"
                + " VALUES (?, 'after@v021.test', 'After Bettor', 'player') RETURNING id",
            Long.class,
            "v021-after-" + System.nanoTime());

    // Create quinielas (pool_id = 1, the seeded pool).
    beforeQuinielaId =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?) RETURNING id",
            Long.class,
            beforeUserId);

    afterQuinielaId =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id) VALUES (1, ?) RETURNING id",
            Long.class,
            afterUserId);

    // bet placed BEFORE kickoff: created_at = 3 hours ago (1 hour before the 2-hour-ago kickoff).
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, created_at, updated_at)"
            + " VALUES (?, ?, 2, 1, NOW() - INTERVAL '3 hours', NOW() - INTERVAL '3 hours')",
        beforeQuinielaId,
        matchId);

    // bet placed AFTER kickoff: created_at = 1 hour ago (1 hour AFTER the 2-hour-ago kickoff).
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, created_at, updated_at)"
            + " VALUES (?, ?, 2, 1, NOW() - INTERVAL '1 hour', NOW() - INTERVAL '1 hour')",
        afterQuinielaId,
        matchId);
  }

  // ── (a) Column existence ────────────────────────────────────────────────────

  @Test
  void betPointsColumnExists() {
    var cols =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns"
                + " WHERE table_name = 'bet' AND table_schema = 'public'",
            String.class);
    assertThat(cols).contains("points");
  }

  // ── (b) Pre-kickoff bet gets scored correctly ────────────────────────────────

  @Test
  void preKickoffBetGetsPointsAndQuinielaMatches() {
    // Score the match: 2-1. The pre-kickoff bet is also 2-1 → exact score.
    // Additive scoring: outcome(3) + T1(2) + T2(2) = 7 (group, ×1).
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = ?", matchId);

    int betPoints =
        jdbc.queryForObject(
            "SELECT points FROM bet WHERE quiniela_id = ? AND match_id = ?",
            Integer.class,
            beforeQuinielaId,
            matchId);
    assertThat(betPoints).isEqualTo(7);

    int quinielaPoints =
        jdbc.queryForObject(
            "SELECT points FROM quiniela WHERE id = ?", Integer.class, beforeQuinielaId);
    assertThat(quinielaPoints).isEqualTo(7);
  }

  // ── (c) Post-kickoff bet is NOT credited — on first score and on re-score ─────

  @Test
  void postKickoffBetStaysZeroOnFirstScore() {
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = ?", matchId);

    int betPoints =
        jdbc.queryForObject(
            "SELECT points FROM bet WHERE quiniela_id = ? AND match_id = ?",
            Integer.class,
            afterQuinielaId,
            matchId);
    assertThat(betPoints).isEqualTo(0);

    int quinielaPoints =
        jdbc.queryForObject(
            "SELECT points FROM quiniela WHERE id = ?", Integer.class, afterQuinielaId);
    assertThat(quinielaPoints).isEqualTo(0);
  }

  @Test
  void postKickoffBetStaysZeroOnRescore_landmineDearmed() {
    // First score
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = ?", matchId);

    // Re-score (correction): different result — the post-kickoff bet is still ignored.
    jdbc.update("UPDATE match SET score_t1 = 3, score_t2 = 0 WHERE id = ?", matchId);

    int betPoints =
        jdbc.queryForObject(
            "SELECT points FROM bet WHERE quiniela_id = ? AND match_id = ?",
            Integer.class,
            afterQuinielaId,
            matchId);
    assertThat(betPoints).isEqualTo(0);

    int quinielaPoints =
        jdbc.queryForObject(
            "SELECT points FROM quiniela WHERE id = ?", Integer.class, afterQuinielaId);
    assertThat(quinielaPoints).isEqualTo(0);
  }

  // ── (d) SUM(bet.points over played) = quiniela.points invariant ──────────────

  @Test
  void sumBetPointsEqualsQuinielaPoints() {
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = ?", matchId);

    // For BEFORE bettor: SUM = 7, quiniela.points = 7.
    Integer beforeSum =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(b.points), 0) FROM bet b"
                + " JOIN match m ON m.id = b.match_id"
                + " WHERE b.quiniela_id = ? AND m.played",
            Integer.class,
            beforeQuinielaId);
    Integer beforeTotal =
        jdbc.queryForObject(
            "SELECT points FROM quiniela WHERE id = ?", Integer.class, beforeQuinielaId);
    assertThat(beforeSum).isEqualTo(beforeTotal);

    // For AFTER bettor: SUM = 0, quiniela.points = 0.
    Integer afterSum =
        jdbc.queryForObject(
            "SELECT COALESCE(SUM(b.points), 0) FROM bet b"
                + " JOIN match m ON m.id = b.match_id"
                + " WHERE b.quiniela_id = ? AND m.played",
            Integer.class,
            afterQuinielaId);
    Integer afterTotal =
        jdbc.queryForObject(
            "SELECT points FROM quiniela WHERE id = ?", Integer.class, afterQuinielaId);
    assertThat(afterSum).isEqualTo(afterTotal);
  }

  // ── bonus: re-score updates pre-kickoff bet.points correctly ────────────────

  @Test
  void preKickoffBetPointsUpdateOnRescore() {
    // First score: 2-1. bet 2-1 → 7 pts.
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 1, played = TRUE WHERE id = ?", matchId);
    int firstPoints =
        jdbc.queryForObject(
            "SELECT points FROM bet WHERE quiniela_id = ? AND match_id = ?",
            Integer.class,
            beforeQuinielaId,
            matchId);
    assertThat(firstPoints).isEqualTo(7);

    // Re-score: 2-0. bet 2-1 vs 2-0: outcome(3) + T1 exact(2) = 5 pts.
    jdbc.update("UPDATE match SET score_t1 = 2, score_t2 = 0 WHERE id = ?", matchId);
    int rescorePoints =
        jdbc.queryForObject(
            "SELECT points FROM bet WHERE quiniela_id = ? AND match_id = ?",
            Integer.class,
            beforeQuinielaId,
            matchId);
    assertThat(rescorePoints).isEqualTo(5);

    int quinielaPoints =
        jdbc.queryForObject(
            "SELECT points FROM quiniela WHERE id = ?", Integer.class, beforeQuinielaId);
    assertThat(quinielaPoints).isEqualTo(5);
  }
}
