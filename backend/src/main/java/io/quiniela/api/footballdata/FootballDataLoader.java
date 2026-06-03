package io.quiniela.api.footballdata;

import io.quiniela.api.match.Round;
import io.quiniela.api.match.RoundRepository;
import io.quiniela.api.team.TeamRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs once at startup. If the team table is empty AND the football-data integration is enabled,
 * fetch teams + standings + matches from football-data.org and populate. Idempotent: skips if team
 * count > 0.
 *
 * <p>Failures (auth, network, parse) are logged at WARN — startup never fails because of a missing
 * or broken external dep. Admin can re-trigger by truncating team + match and restarting the
 * service.
 */
@Component
public class FootballDataLoader implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(FootballDataLoader.class);
  private static final Long TOURNAMENT_ID = 1L;

  private final boolean enabled;
  private final String competitionCode;
  private final FootballDataClient client;
  private final TeamRepository teams;
  private final RoundRepository rounds;
  private final JdbcTemplate jdbc;

  public FootballDataLoader(
      @Value("${app.football-data.enabled:false}") boolean enabled,
      @Value("${app.football-data.competition-code:WC}") String competitionCode,
      FootballDataClient client,
      TeamRepository teams,
      RoundRepository rounds,
      DataSource dataSource) {
    this.enabled = enabled;
    this.competitionCode = competitionCode;
    this.client = client;
    this.teams = teams;
    this.rounds = rounds;
    this.jdbc = new JdbcTemplate(dataSource);
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!enabled) {
      log.info("football-data loader disabled (app.football-data.enabled=false)");
      return;
    }
    // Backfill flag_emoji for any team rows missing it. Idempotent.
    // Guarded behind `enabled` so the H2 smoke test (Flyway disabled, no
    // team table) doesn't try to query a missing relation.
    try {
      backfillFlagEmojis();
    } catch (Exception e) {
      log.warn("football-data backfill failed; flag emojis may be missing", e);
    }
    if (teams.count() > 0) {
      log.info("football-data loader skipped (team table already populated)");
      return;
    }
    try {
      load();
    } catch (Exception e) {
      log.warn("football-data load failed; team + match tables remain empty", e);
    }
  }

  /**
   * Backfill flag_emoji for any team row missing it. Idempotent — only updates rows where
   * flag_emoji IS NULL. Safe to run on every startup.
   */
  @Transactional
  void backfillFlagEmojis() {
    java.util.List<java.util.Map<String, Object>> rows =
        jdbc.queryForList(
            "SELECT id, code FROM team WHERE flag_emoji IS NULL AND code IS NOT NULL");
    int updated = 0;
    int skipped = 0;
    for (java.util.Map<String, Object> row : rows) {
      String code = (String) row.get("code");
      Long id = ((Number) row.get("id")).longValue();
      String emoji = FlagEmojis.toEmoji(code);
      if (emoji == null) {
        skipped++;
        continue;
      }
      jdbc.update("UPDATE team SET flag_emoji = ? WHERE id = ?", emoji, id);
      updated++;
    }
    if (updated > 0 || skipped > 0) {
      log.info("football-data loader: backfilled {} flag emojis ({} unmapped)", updated, skipped);
    }
  }

  @Transactional
  void load() {
    log.info("football-data loader: fetching competition {}", competitionCode);

    // Build a map of teamId -> group letter from /standings.
    Map<Long, String> groupByTeam = new HashMap<>();
    try {
      var standings = client.getStandings(competitionCode);
      if (standings != null && standings.standings() != null) {
        for (var g : standings.standings()) {
          if (!"TOTAL".equals(g.type())) continue; // skip HOME/AWAY duplicates
          String groupLetter = mapGroupName(g.group());
          if (groupLetter == null) continue;
          for (var row : g.table()) {
            groupByTeam.put(row.team().id(), groupLetter);
          }
        }
      }
    } catch (Exception e) {
      log.warn("football-data standings call failed; teams will be inserted without group_code", e);
    }

    // Insert teams.
    var teamsResp = client.getTeams(competitionCode);
    int teamsInserted = 0;
    if (teamsResp != null && teamsResp.teams() != null) {
      for (var t : teamsResp.teams()) {
        String tla = t.tla() != null ? t.tla() : ("X" + t.id());
        String name = t.name() != null ? t.name() : tla;
        String groupCode = groupByTeam.get(t.id());
        String flagEmoji = FlagEmojis.toEmoji(tla);
        jdbc.update(
            "INSERT INTO team (id, tournament_id, code, name, group_code, flag_emoji) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING",
            t.id(),
            TOURNAMENT_ID,
            tla,
            name,
            groupCode,
            flagEmoji);
        teamsInserted++;
      }
    }
    // Bump the BIGSERIAL so future manual inserts don't collide with API ids.
    jdbc.execute("SELECT setval('team_id_seq', GREATEST(1, (SELECT MAX(id) FROM team)))");
    log.info("football-data loader: inserted {} teams", teamsInserted);

    // Insert matches.
    Map<String, Long> roundByCode = new HashMap<>();
    for (Round r : rounds.findAll()) roundByCode.put(r.getCode(), r.getId());

    var matchesResp = client.getMatches(competitionCode);
    int matchesInserted = 0;
    if (matchesResp != null && matchesResp.matches() != null) {
      for (var m : matchesResp.matches()) {
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

        jdbc.update(
            "INSERT INTO match "
                + "(id, tournament_id, round_id, group_code, team_1_id, team_2_id, "
                + " score_t1, score_t2, played, kickoff_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO NOTHING",
            m.id(),
            TOURNAMENT_ID,
            roundId,
            groupCode,
            team1Id,
            team2Id,
            scoreT1,
            scoreT2,
            played,
            java.sql.Timestamp.from(kickoff));
        matchesInserted++;
      }
    }
    jdbc.execute("SELECT setval('match_id_seq', GREATEST(1, (SELECT MAX(id) FROM match)))");
    log.info("football-data loader: inserted {} matches", matchesInserted);
  }

  /** Map football-data.org's "Group A" -> "A". Returns null for unrecognized values. */
  static String mapGroupName(String apiGroup) {
    if (apiGroup == null) return null;
    if (apiGroup.startsWith("Group ") && apiGroup.length() == 7) {
      return apiGroup.substring(6, 7);
    }
    if (apiGroup.startsWith("GROUP_") && apiGroup.length() == 7) {
      return apiGroup.substring(6, 7);
    }
    return null;
  }

  /**
   * The team that progresses from a finished knockout, per football-data.org's score.winner. Works
   * for both regulation/ET wins and penalty shootouts (winner names the progressing side). Returns
   * null for a true draw (group stage) or missing data.
   */
  static Long advancingTeamId(FootballDataClient.MatchApi m) {
    if (m.score() == null || m.score().winner() == null) return null;
    return switch (m.score().winner()) {
      case "HOME_TEAM" -> m.homeTeam() != null ? m.homeTeam().id() : null;
      case "AWAY_TEAM" -> m.awayTeam() != null ? m.awayTeam().id() : null;
      default -> null; // DRAW or unrecognized
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
