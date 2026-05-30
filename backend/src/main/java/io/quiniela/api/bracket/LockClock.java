package io.quiniela.api.bracket;

import java.time.Instant;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LockClock {

  private final JdbcTemplate jdbc;

  public LockClock(DataSource ds) {
    this.jdbc = new JdbcTemplate(ds);
  }

  public record TournamentDeadlines(Instant groupStageDeadline, Instant knockoutDeadline) {}

  @Transactional(readOnly = true)
  public TournamentDeadlines fetchTournamentDeadlines(Long tournamentId) {
    return jdbc.queryForObject(
        "SELECT group_stage_deadline, knockout_deadline FROM tournament WHERE id = ?",
        (rs, n) -> {
          var gs = rs.getTimestamp("group_stage_deadline");
          var ko = rs.getTimestamp("knockout_deadline");
          return new TournamentDeadlines(
              gs == null ? null : gs.toInstant(), ko == null ? null : ko.toInstant());
        },
        tournamentId);
  }

  /** True once the group-stage deadline has passed. */
  public static boolean isGroupRevealable(Instant now, TournamentDeadlines d) {
    return d.groupStageDeadline() != null && now.isAfter(d.groupStageDeadline());
  }

  /** True once the knockout deadline has passed. */
  public static boolean isKnockoutRevealable(Instant now, TournamentDeadlines d) {
    return d.knockoutDeadline() != null && now.isAfter(d.knockoutDeadline());
  }

  /**
   * Whether a match's picks may be revealed to OTHER players: group-stage matches (round code
   * "GROUP") gate on the group deadline; everything else on the knockout deadline. A player's own
   * picks are always visible to themselves regardless.
   */
  public static boolean isMatchRevealable(Instant now, TournamentDeadlines d, String roundCode) {
    return "GROUP".equals(roundCode) ? isGroupRevealable(now, d) : isKnockoutRevealable(now, d);
  }
}
