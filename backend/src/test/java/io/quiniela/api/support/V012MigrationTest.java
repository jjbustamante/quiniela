package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V012MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void usersHaveTimezoneColumnDefaultingToBogota() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'users' AND table_schema = 'public'",
            String.class);
    assertThat(columns).contains("timezone");

    jdbc.update(
        "INSERT INTO users (google_sub, email, role) VALUES ('tz-default', 'tz@example.com', 'player')");
    String tz =
        jdbc.queryForObject(
            "SELECT timezone FROM users WHERE google_sub = 'tz-default'", String.class);
    assertThat(tz).isEqualTo("America/Bogota");
  }
}
