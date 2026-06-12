package io.quiniela.api.footballdata;

import io.quiniela.api.footballdata.FootballDataClient.CompetitionMatchesResponse;
import io.quiniela.api.footballdata.FootballDataClient.MatchApi;
import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared football-data result-sync logic. The UPSERT freezes already-played matches (WHERE NOT
 * match.played) so a re-sync can never re-score a finalized game — critical once betting is
 * reopened and bets may be edited after a game was already scored.
 */
@Service
public class FootballDataSyncService {

  static final Long TOURNAMENT_ID = 1L;
  private static final Logger log = LoggerFactory.getLogger(FootballDataSyncService.class);

  private final RoundRepository rounds;
  private final JdbcTemplate jdbc;

  public FootballDataSyncService(RoundRepository rounds, DataSource dataSource) {
    this.rounds = rounds;
    this.jdbc = new JdbcTemplate(dataSource);
  }

  /** UPSERT every match in the payload. Already-played rows are frozen (no UPDATE, no trigger). */
  @Transactional
  public int upsertMatches(CompetitionMatchesResponse resp) {
    if (resp == null || resp.matches() == null) return 0;

    Map<String, Long> roundByCode = new HashMap<>();
    for (Round r : rounds.findAll()) roundByCode.put(r.getCode(), r.getId());

    int n = 0;
    for (MatchApi m : resp.matches()) {
      String roundCode = mapStageToRoundCode(m.stage());
      Long roundId = roundByCode.get(roundCode);
      if (roundId == null) {
        log.debug("Skipping match {}: unmapped stage {}", m.id(), m.stage());
        continue;
      }
      String groupCode = "GROUP".equals(roundCode) ? mapGroupName(m.group()) : null;
      Long team1Id = m.homeTeam() != null ? m.homeTeam().id() : null;
      Long team2Id = m.awayTeam() != null ? m.awayTeam().id() : null;
      Instant kickoff = m.utcDate() != null ? Instant.parse(m.utcDate()) : Instant.now();
      Integer scoreT1 =
          m.score() != null && m.score().fullTime() != null ? m.score().fullTime().home() : null;
      Integer scoreT2 =
          m.score() != null && m.score().fullTime() != null ? m.score().fullTime().away() : null;
      boolean played = "FINISHED".equals(m.status());
      Long advancedTeamId = advancingTeamId(m);

      // WHERE NOT match.played freezes finalized games: the UPDATE is suppressed entirely,
      // so the BEFORE UPDATE points trigger never fires for an already-scored match.
      jdbc.update(
          "INSERT INTO match "
              + "(id, tournament_id, round_id, group_code, team_1_id, team_2_id, "
              + " score_t1, score_t2, advanced_team_id, played, kickoff_at) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
              + "ON CONFLICT (id) DO UPDATE SET "
              + "  team_1_id = COALESCE(EXCLUDED.team_1_id, match.team_1_id), "
              + "  team_2_id = COALESCE(EXCLUDED.team_2_id, match.team_2_id), "
              + "  score_t1 = EXCLUDED.score_t1, "
              + "  score_t2 = EXCLUDED.score_t2, "
              + "  advanced_team_id = EXCLUDED.advanced_team_id, "
              + "  played = EXCLUDED.played, "
              + "  kickoff_at = EXCLUDED.kickoff_at "
              + "WHERE NOT match.played",
          m.id(),
          TOURNAMENT_ID,
          roundId,
          groupCode,
          team1Id,
          team2Id,
          scoreT1,
          scoreT2,
          advancedTeamId,
          played,
          java.sql.Timestamp.from(kickoff));
      n++;
    }
    jdbc.execute("SELECT setval('match_id_seq', GREATEST(1, (SELECT MAX(id) FROM match)))");
    return n;
  }

  /** Map football-data.org's "Group A" -> "A". Returns null for unrecognized values. */
  static String mapGroupName(String apiGroup) {
    if (apiGroup == null) return null;
    if (apiGroup.startsWith("Group ") && apiGroup.length() == 7) return apiGroup.substring(6, 7);
    if (apiGroup.startsWith("GROUP_") && apiGroup.length() == 7) return apiGroup.substring(6, 7);
    return null;
  }

  /** Progressing team from a finished knockout (winner names the side; null on draw/missing). */
  static Long advancingTeamId(MatchApi m) {
    if (m.score() == null || m.score().winner() == null) return null;
    return switch (m.score().winner()) {
      case "HOME_TEAM" -> m.homeTeam() != null ? m.homeTeam().id() : null;
      case "AWAY_TEAM" -> m.awayTeam() != null ? m.awayTeam().id() : null;
      default -> null;
    };
  }

  /** Map football-data.org match.stage codes to our round.code values. */
  static String mapStageToRoundCode(String apiStage) {
    if (apiStage == null) return null;
    return switch (apiStage) {
      case "GROUP_STAGE" -> "GROUP";
      case "LAST_32", "ROUND_OF_32" -> "R32";
      case "LAST_16", "ROUND_OF_16" -> "R16";
      case "QUARTER_FINALS", "QUARTERFINALS" -> "QF";
      case "SEMI_FINALS", "SEMIFINALS" -> "SF";
      case "THIRD_PLACE", "PLAY_OFF_FOR_THIRD_PLACE" -> "THIRD_PLACE";
      case "FINAL" -> "FINAL";
      default -> null;
    };
  }
}
