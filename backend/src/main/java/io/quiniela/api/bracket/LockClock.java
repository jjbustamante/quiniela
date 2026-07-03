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
}
