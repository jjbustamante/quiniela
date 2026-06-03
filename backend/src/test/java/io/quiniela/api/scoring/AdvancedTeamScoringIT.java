package io.quiniela.api.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V017: a knockout penalty draw stores advanced_team_id (survives the draw-nulling that hits
 * winner_id), and the predicted-winner bonus is scored off advanced_team_id.
 */
class AdvancedTeamScoringIT extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;
  JdbcTemplate jdbc;

  // A real knockout match from the seed (round R32). Resolved in setUp.
  Long koMatchId;
  Long koTeam1;
  Long koTeam2;

  @BeforeEach
  void setUp() {
    jdbc = new JdbcTemplate(dataSource);
    koMatchId =
        jdbc.queryForObject(
            "SELECT m.id FROM match m JOIN round r ON r.id = m.round_id"
                + " WHERE r.code = 'R32' ORDER BY m.id LIMIT 1",
            Long.class);
    // The V007 test seed inserts R32 matches with NULL team slots (teams fill in
    // later from group standings). Assign two real teams so we can record a result
    // with a concrete advancing team. Restored to NULL in @AfterEach because the
    // Testcontainers Postgres is a shared singleton (see AbstractIntegrationTest).
    var teamIds = jdbc.queryForList("SELECT id FROM team ORDER BY id LIMIT 2", Long.class);
    koTeam1 = teamIds.get(0);
    koTeam2 = teamIds.get(1);
    jdbc.update(
        "UPDATE match SET team_1_id = ?, team_2_id = ? WHERE id = ?", koTeam1, koTeam2, koMatchId);
  }

  @AfterEach
  void restore() {
    jdbc.update("DELETE FROM bet WHERE match_id = ?", koMatchId);
    jdbc.update(
        "DELETE FROM quiniela WHERE user_id IN (SELECT id FROM users WHERE google_sub ="
            + " 'adv-team-test-user')");
    jdbc.update("DELETE FROM users WHERE google_sub = 'adv-team-test-user'");
    jdbc.update(
        "UPDATE match SET score_t1 = NULL, score_t2 = NULL, played = FALSE, winner_id = NULL,"
            + " advanced_team_id = NULL, team_1_id = NULL, team_2_id = NULL WHERE id = ?",
        koMatchId);
  }

  @Test
  void penaltyDrawKeepsAdvancedTeamAndAwardsBonus() {
    // Insert a test user with a stable google_sub so @AfterEach can clean it up.
    Long testUserId =
        jdbc.queryForObject(
            "INSERT INTO users (google_sub, email, display_name, role, is_bot)"
                + " VALUES ('adv-team-test-user', 'adv@test.com', 'AdvTest', 'player', FALSE)"
                + " ON CONFLICT (google_sub) DO UPDATE SET display_name = EXCLUDED.display_name"
                + " RETURNING id",
            Long.class);

    // A quiniela that bet a 1-1 draw and predicted koTeam2 advances.
    Long quinielaId =
        jdbc.queryForObject(
            "INSERT INTO quiniela (pool_id, user_id, points, created_at, updated_at)"
                + " VALUES (1, ?, 0, NOW(), NOW()) RETURNING id",
            Long.class,
            testUserId);
    jdbc.update(
        "INSERT INTO bet (quiniela_id, match_id, score_t1, score_t2, predicted_winner_id,"
            + " created_at, updated_at) VALUES (?, ?, 1, 1, ?, NOW(), NOW())",
        quinielaId,
        koMatchId,
        koTeam2);

    // Record a 1-1 penalty draw, koTeam2 advanced.
    jdbc.update(
        "UPDATE match SET score_t1 = 1, score_t2 = 1, played = TRUE, advanced_team_id = ?"
            + " WHERE id = ?",
        koTeam2,
        koMatchId);

    // advanced_team_id persisted (NOT nulled like winner_id is on a draw).
    Long advanced =
        jdbc.queryForObject(
            "SELECT advanced_team_id FROM match WHERE id = ?", Long.class, koMatchId);
    assertThat(advanced).isEqualTo(koTeam2);
    Long winnerId =
        jdbc.queryForObject("SELECT winner_id FROM match WHERE id = ?", Long.class, koMatchId);
    assertThat(winnerId).isNull(); // score-derived, still null on a draw

    // Knockout exact 1-1 (2+2) + correct advancement outcome (3) = 7, doubled = 14.
    Long points =
        jdbc.queryForObject("SELECT points FROM quiniela WHERE id = ?", Long.class, quinielaId);
    assertThat(points).isEqualTo(14L);
  }
}
