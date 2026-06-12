package io.quiniela.api.matches;

import io.quiniela.api.scoring.ScoreBreakdown;
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
      boolean live,
      ScorePair yourPick,
      Integer pointsEarned,
      TeamRef pickWinner,
      TeamRef winner,
      ScoreBreakdown breakdown) {}

  public record MatchesView(
      Instant serverTime, List<MatchRow> past, List<MatchRow> today, List<MatchRow> upcoming) {}

  /**
   * One round trip: join match → round → teams → caller's bet, compute pointsEarned via V016/V017's
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
            // V016/V017's score_match_for_bet returns 0 for unplayed matches because actual scores
            // are null; we surface null in that case instead of 0 so the UI can distinguish
            // "no points yet" from "you got zero".
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
              aw.code           AS aw_code,
              aw.name           AS aw_name,
              aw.flag_emoji     AS aw_flag,
              m.score_t1        AS m_score_t1,
              m.score_t2        AS m_score_t2,
              m.played          AS played,
              b.score_t1        AS bet_t1,
              b.score_t2        AS bet_t2,
              b.predicted_winner_id AS pred_winner,
              m.advanced_team_id    AS adv_team,
              r.points_multiplier   AS mult
            FROM match m
            JOIN round r ON r.id = m.round_id
            LEFT JOIN team t1 ON t1.id = m.team_1_id
            LEFT JOIN team t2 ON t2.id = m.team_2_id
            LEFT JOIN bet  b  ON b.match_id = m.id AND b.quiniela_id = ?
            LEFT JOIN team bw ON bw.id = b.predicted_winner_id
            LEFT JOIN team aw ON aw.id = m.advanced_team_id
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
              TeamRef winner =
                  rs.getString("aw_code") == null
                      ? null
                      : new TeamRef(
                          rs.getString("aw_code"),
                          rs.getString("aw_name"),
                          rs.getString("aw_flag"));
              Long predWinner = (Long) rs.getObject("pred_winner");
              Long advTeam = (Long) rs.getObject("adv_team");
              boolean played = rs.getBoolean("played");
              boolean live = !played && rs.getObject("m_score_t1") != null;
              ScoreBreakdown breakdown =
                  (rs.getObject("m_score_t1") != null && rs.getObject("bet_t1") != null)
                      ? ScoreBreakdown.of(
                          !"GROUP".equals(rs.getString("round_code")),
                          rs.getInt("bet_t1"),
                          rs.getInt("bet_t2"),
                          (Integer) rs.getObject("m_score_t1"),
                          (Integer) rs.getObject("m_score_t2"),
                          predWinner,
                          advTeam,
                          rs.getInt("mult"))
                      : null;
              Integer pointsEarned = breakdown == null ? null : breakdown.total();
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
                  played,
                  live,
                  yourPick,
                  pointsEarned,
                  pickWinner,
                  winner,
                  breakdown);
            },
            quinielaId == null ? -1L : quinielaId,
            DEFAULT_TOURNAMENT_ID);

    // Bucket by the caller's LOCAL calendar day (their display timezone), not UTC — otherwise a
    // match that kicked off late "yesterday" in their zone (e.g. 21:00 Bogotá = 02:00 UTC) shows
    // under "Today" just because it's still the same UTC date. The day boundaries are computed in
    // the user's zone (COALESCE to America/Bogota for users who never set one) and the +1 day is
    // done in local time so it stays correct across any DST transition.
    java.time.Instant[] bounds =
        jdbc.queryForObject(
            "SELECT DATE_TRUNC('day', NOW() AT TIME ZONE u.tz) AT TIME ZONE u.tz AS today_start, "
                + "(DATE_TRUNC('day', NOW() AT TIME ZONE u.tz) + INTERVAL '1 day') AT TIME ZONE u.tz "
                + "  AS tomorrow_start "
                + "FROM (SELECT COALESCE((SELECT timezone FROM users WHERE id = ?), "
                + "                      'America/Bogota') AS tz) u",
            (rs, n) ->
                new java.time.Instant[] {
                  rs.getTimestamp("today_start").toInstant(),
                  rs.getTimestamp("tomorrow_start").toInstant()
                },
            callerUserId);
    var startOfToday = bounds[0];
    var startOfTomorrow = bounds[1];

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
