package io.quiniela.api.ranking;

import io.quiniela.api.ranking.RankingView.RankingEntry;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RankingService {

  private static final Long DEFAULT_POOL_ID = 1L;

  private final JdbcTemplate jdbc;

  public RankingService(DataSource ds) {
    this.jdbc = new JdbcTemplate(ds);
  }

  @Transactional(readOnly = true)
  public RankingView getRanking(Long callerUserId) {
    // RANK() shares the ordinal on ties (1, 1, 3); ROW_NUMBER() would give 1, 2, 3.
    // Sort: points DESC then display_name ASC for stable per-page ordering on ties.
    // delta stays null in v1; v1.1 adds a snapshot table.
    List<RankingEntry> entries =
        jdbc.query(
            """
            SELECT
              RANK() OVER (ORDER BY q.points DESC) AS rk,
              u.id            AS user_id,
              u.display_name  AS display_name,
              q.points        AS points
            FROM quiniela q
            JOIN users u ON u.id = q.user_id
            WHERE q.pool_id = ?
            ORDER BY q.points DESC, u.display_name ASC
            """,
            (rs, n) -> {
              int rank = rs.getInt("rk");
              long userId = rs.getLong("user_id");
              String displayName = rs.getString("display_name");
              int points = rs.getInt("points");
              boolean isYou = callerUserId != null && callerUserId == userId;
              return new RankingEntry(rank, userId, displayName, points, null, isYou);
            },
            DEFAULT_POOL_ID);

    String updatedAt = computeUpdatedAt();
    return new RankingView(List.copyOf(new ArrayList<>(entries)), updatedAt);
  }

  /**
   * Snapshot time for the leaderboard: max(updated_at) across the pool's quinielas. Falls back to
   * "now" when the pool is empty so the UI never has to handle null.
   */
  private String computeUpdatedAt() {
    var ts =
        jdbc.queryForObject(
            "SELECT COALESCE(MAX(updated_at), NOW()) FROM quiniela WHERE pool_id = ?",
            java.sql.Timestamp.class,
            DEFAULT_POOL_ID);
    return ts == null ? null : ts.toInstant().toString();
  }
}
