package io.quiniela.api.paul;

import static org.assertj.core.api.Assertions.assertThat;

import io.quiniela.api.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class PaulSchemaIT extends AbstractIntegrationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  void paulBotUserIsSeededAsBot() {
    jdbc.update(
        "INSERT INTO users (google_sub, email, display_name, role, is_bot) "
            + "VALUES ('paul-bot-oracle', 'paul@laquinieladelospanas.com', 'Pulpo Paul 🐙', 'player', TRUE) "
            + "ON CONFLICT (google_sub) DO NOTHING");
    Boolean isBot =
        jdbc.queryForObject(
            "SELECT is_bot FROM users WHERE google_sub = 'paul-bot-oracle'", Boolean.class);
    assertThat(isBot).isTrue();
    String role =
        jdbc.queryForObject(
            "SELECT role FROM users WHERE google_sub = 'paul-bot-oracle'", String.class);
    assertThat(role).isEqualTo("player");
  }

  @Test
  void paulPredictionTableExists() {
    Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM paul_prediction", Integer.class);
    assertThat(count).isZero();
  }

  @Test
  void newRealUsersDefaultToNonBot() {
    jdbc.update(
        "INSERT INTO users (google_sub, email, display_name, role) "
            + "VALUES ('g-real', 'r@example.com', 'Real', 'player')");
    Boolean isBot =
        jdbc.queryForObject("SELECT is_bot FROM users WHERE google_sub = 'g-real'", Boolean.class);
    assertThat(isBot).isFalse();
  }
}
