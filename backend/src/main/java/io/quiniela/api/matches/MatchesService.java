package io.quiniela.api.matches;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchesService {

  private static final Long DEFAULT_POOL_ID = 1L;
  private static final Long DEFAULT_TOURNAMENT_ID = 1L;

  private final JdbcTemplate jdbc;

  public MatchesService(DataSource ds) {
    this.jdbc = new JdbcTemplate(ds);
  }

  public record TeamRef(String code, String name, String flag) {}

  public record ScorePair(Integer t1, Integer t2) {}

  public record MatchRow(
      Long id,
      String roundCode,
      String groupCode,
      Instant kickoffAt,
      TeamRef team1,
      TeamRef team2,
      ScorePair score,
      boolean played,
      ScorePair yourPick,
      Integer pointsEarned,
      TeamRef pickWinner) {}

  public record MatchesView(
      Instant serverTime, List<MatchRow> past, List<MatchRow> today, List<MatchRow> upcoming) {}

  /**
   * One round trip: join match → round → teams → caller's bet, compute pointsEarned via V010's
   * score_match_for_bet so the page never drifts from the trigger. Partition Java-side by UTC
   * kickoff bucket (past: kickoff < start_of_today; today: [start_of_today, start_of_tomorrow);
   * upcoming: kickoff >= start_of_tomorrow). pointsEarned is null when the match is unplayed or the
   * user hasn't bet.
   */
  @Transactional(readOnly = true)
  public MatchesView getMatches(Long callerUserId) {
    Long quinielaId =
        jdbc.query(
            "SELECT id FROM quiniela WHERE pool_id = ? AND user_id = ?",
            rs -> rs.next() ? rs.getLong("id") : null,
            DEFAULT_POOL_ID,
            callerUserId);

    List<MatchRow> rows =
        jdbc.query(
            // Single query: every match in the tournament + my bet (if any) + computed points.
            // score_match_for_bet returns 0 for unplayed matches because actual scores are null;
            // we surface null in that case instead of 0 so the UI can distinguish "no points yet"
            // from "you got zero".
            """
            SELECT
              m.id              AS match_id,
              r.code            AS round_code,
              m.group_code      AS group_code,
              m.kickoff_at      AS kickoff_at,
              t1.code           AS t1_code,
              t1.name           AS t1_name,
              t1.flag_emoji     AS t1_flag,
              t2.code           AS t2_code,
              t2.name           AS t2_name,
              t2.flag_emoji     AS t2_flag,
              bw.code           AS pw_code,
              bw.name           AS pw_name,
              bw.flag_emoji     AS pw_flag,
              m.score_t1        AS m_score_t1,
              m.score_t2        AS m_score_t2,
              m.played          AS played,
              b.score_t1        AS bet_t1,
              b.score_t2        AS bet_t2,
              CASE
                WHEN m.played AND b.score_t1 IS NOT NULL THEN
                  score_match_for_bet(
                    r.code <> 'GROUP',
                    b.score_t1, b.score_t2,
                    m.score_t1, m.score_t2)
                ELSE NULL
              END               AS points_earned
            FROM match m
            JOIN round r ON r.id = m.round_id
            LEFT JOIN team t1 ON t1.id = m.team_1_id
            LEFT JOIN team t2 ON t2.id = m.team_2_id
            LEFT JOIN bet  b  ON b.match_id = m.id AND b.quiniela_id = ?
            LEFT JOIN team bw ON bw.id = b.predicted_winner_id
            WHERE m.tournament_id = ?
            ORDER BY m.kickoff_at ASC
            """,
            (rs, n) -> {
              ScorePair score =
                  rs.getObject("m_score_t1") == null && rs.getObject("m_score_t2") == null
                      ? null
                      : new ScorePair(
                          (Integer) rs.getObject("m_score_t1"),
                          (Integer) rs.getObject("m_score_t2"));
              ScorePair yourPick =
                  rs.getObject("bet_t1") == null
                      ? null
                      : new ScorePair(rs.getInt("bet_t1"), rs.getInt("bet_t2"));
              TeamRef pickWinner =
                  rs.getString("pw_code") == null
                      ? null
                      : new TeamRef(
                          rs.getString("pw_code"),
                          rs.getString("pw_name"),
                          rs.getString("pw_flag"));
              return new MatchRow(
                  rs.getLong("match_id"),
                  rs.getString("round_code"),
                  rs.getString("group_code"),
                  rs.getTimestamp("kickoff_at").toInstant(),
                  new TeamRef(
                      rs.getString("t1_code"), rs.getString("t1_name"), rs.getString("t1_flag")),
                  new TeamRef(
                      rs.getString("t2_code"), rs.getString("t2_name"), rs.getString("t2_flag")),
                  score,
                  rs.getBoolean("played"),
                  yourPick,
                  (Integer) rs.getObject("points_earned"),
                  pickWinner);
            },
            quinielaId == null ? -1L : quinielaId,
            DEFAULT_TOURNAMENT_ID);

    // Bucket by UTC day. NOW() is the server's clock; using LocalDateTime here would be
    // timezone-ambiguous. Compute start_of_today and start_of_tomorrow once.
    var startOfToday =
        jdbc.queryForObject(
                "SELECT DATE_TRUNC('day', NOW() AT TIME ZONE 'UTC') AT TIME ZONE 'UTC'",
                java.sql.Timestamp.class)
            .toInstant();
    var startOfTomorrow = startOfToday.plusSeconds(86_400);

    List<MatchRow> past = new ArrayList<>();
    List<MatchRow> today = new ArrayList<>();
    List<MatchRow> upcoming = new ArrayList<>();
    for (MatchRow r : rows) {
      if (r.kickoffAt().isBefore(startOfToday)) {
        past.add(r);
      } else if (r.kickoffAt().isBefore(startOfTomorrow)) {
        today.add(r);
      } else {
        upcoming.add(r);
      }
    }
    // Past should read newest-first; today + upcoming stay chronological.
    past.sort((a, b) -> b.kickoffAt().compareTo(a.kickoffAt()));

    return new MatchesView(
        Instant.now(), List.copyOf(past), List.copyOf(today), List.copyOf(upcoming));
  }
}
