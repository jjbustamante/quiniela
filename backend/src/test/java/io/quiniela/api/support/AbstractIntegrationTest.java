package io.quiniela.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests that need a real Postgres database.
 *
 * <p>Uses the Testcontainers singleton-container pattern: the container is started once for the
 * entire test JVM and stopped on JVM exit via Ryuk, not per-test-class lifecycle. This avoids the
 * Spring context cache invalidation that occurs when {@code @Testcontainers + @Container} restarts
 * the container between IT test classes.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

  static final PostgreSQLContainer<?> postgres;

  static {
    postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quiniela_test")
            .withUsername("quiniela")
            .withPassword("test");
    postgres.start();
  }

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.enabled", () -> "true");
  }

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

  @org.junit.jupiter.api.BeforeEach
  void cleanWritableTables() {
    // Order matters: payment first (marked_paid_by/marked_settled_by are non-cascade FKs to users).
    jdbcTemplate.execute("DELETE FROM payment");
    jdbcTemplate.execute("DELETE FROM bet");
    jdbcTemplate.execute("DELETE FROM quiniela");
    jdbcTemplate.execute("DELETE FROM pool_membership");
    jdbcTemplate.execute("DELETE FROM users");
  }
}
