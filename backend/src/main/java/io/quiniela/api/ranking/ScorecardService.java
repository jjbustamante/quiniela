package io.quiniela.api.ranking;

import io.quiniela.api.ranking.ScorecardView.MatchScore;
import io.quiniela.api.ranking.ScorecardView.StageScore;
import io.quiniela.api.ranking.ScorecardView.TeamRef;
import io.quiniela.api.scoring.ScoreBreakdown;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Builds a player's scorecard: their played, bet-on matches, each with a {@link ScoreBreakdown},
 * grouped by stage (round sequence order) with per-stage totals. Any player is viewable — only
 * played matches (already revealed) appear, so nothing private is exposed.
 */
@Service
public class ScorecardService {

  private static final Long DEFAULT_POOL_ID = 1L;

  private final JdbcTemplate jdbc;

  public ScorecardService(DataSource ds) {
    this.jdbc = new JdbcTemplate(ds);
  }

  private static final class StageAcc {
    final String roundName;
    final List<MatchScore> matches = new ArrayList<>();
    int points = 0;

    StageAcc(String roundName) {
      this.roundName = roundName;
    }
  }

  @Transactional(readOnly = true)
  public ScorecardView getScorecard(Long userId) {
    var head =
        jdbc.query(
            "SELECT u.display_name, q.points FROM quiniela q JOIN users u ON u.id = q.user_id "
                + "WHERE q.pool_id = ? AND q.user_id = ?",
            rs -> rs.next() ? new String[] {rs.getString(1), String.valueOf(rs.getInt(2))} : null,
            DEFAULT_POOL_ID,
            userId);
    if (head == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No scorecard for this user");
    }
    String displayName = head[0];
    int totalPoints = Integer.parseInt(head[1]);

    Map<String, StageAcc> byRound = new LinkedHashMap<>();
    jdbc.query(
        """
        SELECT r.code AS round_code, r.name AS round_name, r.points_multiplier AS mult,
               m.id AS match_id, m.kickoff_at AS kickoff_at,
               t1.code AS t1_code, t1.name AS t1_name, t1.flag_emoji AS t1_flag,
               t2.code AS t2_code, t2.name AS t2_name, t2.flag_emoji AS t2_flag,
               b.score_t1 AS bet_t1, b.score_t2 AS bet_t2, b.predicted_winner_id AS pred_winner,
               m.score_t1 AS act_t1, m.score_t2 AS act_t2, m.advanced_team_id AS adv_team,
               b.points AS frozen_points, b.created_at AS bet_created_at
        FROM bet b
        JOIN quiniela q ON q.id = b.quiniela_id
        JOIN match m ON m.id = b.match_id
        JOIN round r ON r.id = m.round_id
        LEFT JOIN team t1 ON t1.id = m.team_1_id
        LEFT JOIN team t2 ON t2.id = m.team_2_id
        WHERE q.pool_id = ? AND q.user_id = ? AND m.played = TRUE
        ORDER BY r.sequence ASC, m.kickoff_at ASC, m.id ASC
        """,
        rs -> {
          String roundCode = rs.getString("round_code");
          boolean knockout = !"GROUP".equals(roundCode);
          int mult = rs.getInt("mult");
          int betT1 = rs.getInt("bet_t1");
          int betT2 = rs.getInt("bet_t2");
          Integer actT1 = (Integer) rs.getObject("act_t1");
          Integer actT2 = (Integer) rs.getObject("act_t2");
          Long predWinner = (Long) rs.getObject("pred_winner");
          Long advTeam = (Long) rs.getObject("adv_team");
          int frozenPoints = rs.getInt("frozen_points");
          Timestamp kickoffTs = rs.getTimestamp("kickoff_at");
          Timestamp betCreatedAt = rs.getTimestamp("bet_created_at");

          // Compute live breakdown to detect divergence from frozen points.
          ScoreBreakdown breakdown =
              ScoreBreakdown.of(knockout, betT1, betT2, actT1, actT2, predWinner, advTeam, mult);

          // Determine note: only when live total differs from what was actually frozen.
          String note = null;
          if (breakdown.total() != frozenPoints) {
            note = betCreatedAt.after(kickoffTs) ? "PLACED_AFTER_KICKOFF" : "EDITED_AFTER_KICKOFF";
          }

          MatchScore ms =
              new MatchScore(
                  rs.getLong("match_id"),
                  new TeamRef(
                      rs.getString("t1_code"), rs.getString("t1_name"), rs.getString("t1_flag")),
                  new TeamRef(
                      rs.getString("t2_code"), rs.getString("t2_name"), rs.getString("t2_flag")),
                  kickoffTs.toInstant().toString(),
                  betT1,
                  betT2,
                  actT1,
                  actT2,
                  breakdown,
                  frozenPoints,
                  note);

          String roundName = rs.getString("round_name");
          StageAcc acc = byRound.computeIfAbsent(roundCode, k -> new StageAcc(roundName));
          acc.matches.add(ms);
          // Accumulate frozen points (not live total) for the per-round subtotal.
          acc.points += frozenPoints;
        },
        DEFAULT_POOL_ID,
        userId);

    List<StageScore> stages = new ArrayList<>();
    for (var e : byRound.entrySet()) {
      stages.add(
          new StageScore(
              e.getKey(), e.getValue().roundName, e.getValue().points, e.getValue().matches));
    }
    return new ScorecardView(userId, displayName, totalPoints, stages);
  }
}
