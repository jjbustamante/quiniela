package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V003MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void usersTableHasRoleAndInviteFields() {
    var jdbc = new JdbcTemplate(dataSource);

    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'users' ORDER BY column_name",
            String.class);

    assertThat(columns).contains("role", "invited_by_user_id", "invite_path");
    assertThat(columns).doesNotContain("is_admin");
  }

  @Test
  void poolAndPrizeSplitSeeded() {
    var jdbc = new JdbcTemplate(dataSource);

    Long poolCount = jdbc.queryForObject("SELECT COUNT(*) FROM pool", Long.class);
    Long prizeCount =
        jdbc.queryForObject("SELECT COUNT(*) FROM prize_split WHERE pool_id = 1", Long.class);

    assertThat(poolCount).isEqualTo(1L);
    assertThat(prizeCount).isEqualTo(3L);
  }
}
