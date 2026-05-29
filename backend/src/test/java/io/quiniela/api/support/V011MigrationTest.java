package io.quiniela.api.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class V011MigrationTest extends AbstractIntegrationTest {

  @Autowired DataSource dataSource;

  @Test
  void paymentTableExists() {
    var jdbc = new JdbcTemplate(dataSource);
    var columns =
        jdbc.queryForList(
            "SELECT column_name FROM information_schema.columns "
                + "WHERE table_name = 'payment' AND table_schema = 'public'",
            String.class);
    assertThat(columns)
        .contains(
            "pool_id",
            "user_id",
            "paid",
            "paid_at",
            "marked_paid_by",
            "amount_cents",
            "note",
            "settled",
            "settled_at",
            "marked_settled_by");
  }
}
