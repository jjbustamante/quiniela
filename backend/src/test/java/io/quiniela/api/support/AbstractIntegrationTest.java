package io.quiniela.api.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("quiniela_test")
          .withUsername("quiniela")
          .withPassword("test");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.enabled", () -> "true");
    // Disable Hibernate schema validation: migration tests verify schema
    // via information_schema JDBC queries, not via Hibernate entity mapping.
    // The User entity still references is_admin (fixed in Task 2); without
    // this override, Hibernate's ddl-auto=validate would abort context load.
    r.add("spring.jpa.hibernate.ddl-auto", () -> "none");
  }
}
