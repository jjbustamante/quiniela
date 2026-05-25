package io.quiniela.api.footballdata;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.quiniela.api.team.TeamRepository;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Isolated integration test for FootballDataLoader. Uses its own dedicated Postgres container so
 * that wiping team + match rows does not affect other IT classes that rely on the shared
 * Testcontainers singleton in AbstractIntegrationTest.
 */
@SpringBootTest
@Import(FootballDataLoaderIT.WireMockConfig.class)
class FootballDataLoaderIT {

  // Dedicated container — isolated from AbstractIntegrationTest's singleton.
  static final PostgreSQLContainer<?> postgres;
  static WireMockServer wm;

  static {
    postgres =
        new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("quiniela_loader_test")
            .withUsername("quiniela")
            .withPassword("test");
    postgres.start();

    wm = new WireMockServer(0);
    wm.start();
    WireMock.configureFor("localhost", wm.port());
  }

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    // Datasource — dedicated container
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.flyway.enabled", () -> "true");
    r.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/test-migration");
    // football-data loader — enabled, pointed at WireMock
    r.add("app.football-data.enabled", () -> "true");
    r.add("app.football-data.base-url", () -> "http://localhost:" + wm.port() + "/v4");
    r.add("app.football-data.api-key", () -> "test-key");
    r.add("app.football-data.competition-code", () -> "WC");
  }

  @Autowired DataSource dataSource;
  @Autowired TeamRepository teams;
  @Autowired FootballDataLoader loader;

  @BeforeEach
  void wipeTeamsAndMatches() {
    var jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM bet");
    jdbc.execute("DELETE FROM match");
    jdbc.execute("DELETE FROM team");
    wm.resetAll();
    WireMock.configureFor("localhost", wm.port());
  }

  @AfterEach
  void resetWireMock() {
    wm.resetAll();
  }

  @TestConfiguration
  static class WireMockConfig {
    @Bean
    WireMockServer wireMockServer() {
      return wm;
    }
  }

  @Test
  void loadsTeamsWithGroupAssignmentsAndMatches() {
    stubFor(
        get(urlEqualTo("/v4/competitions/WC/standings"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"standings\":[{\"group\":\"Group A\",\"type\":\"TOTAL\","
                            + "\"table\":[{\"team\":{\"id\":1001,\"name\":\"Equipo Uno\"}},"
                            + "{\"team\":{\"id\":1002,\"name\":\"Equipo Dos\"}}]}]}")));

    stubFor(
        get(urlEqualTo("/v4/competitions/WC/teams"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"teams\":["
                            + "{\"id\":1001,\"name\":\"Equipo Uno\",\"tla\":\"EU1\",\"crest\":null},"
                            + "{\"id\":1002,\"name\":\"Equipo Dos\",\"tla\":\"EU2\",\"crest\":null}]}")));

    stubFor(
        get(urlEqualTo("/v4/competitions/WC/matches"))
            .willReturn(
                aResponse()
                    .withHeader("Content-Type", "application/json")
                    .withBody(
                        "{\"matches\":[{\"id\":5001,\"utcDate\":\"2026-06-11T17:00:00Z\","
                            + "\"status\":\"SCHEDULED\",\"stage\":\"GROUP_STAGE\","
                            + "\"group\":\"Group A\","
                            + "\"homeTeam\":{\"id\":1001,\"name\":\"Equipo Uno\"},"
                            + "\"awayTeam\":{\"id\":1002,\"name\":\"Equipo Dos\"},"
                            + "\"score\":{\"fullTime\":{\"home\":null,\"away\":null}}}]}")));

    loader.run(null);

    var jdbc = new JdbcTemplate(dataSource);
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM team", Long.class)).isEqualTo(2L);
    String groupOfOne =
        jdbc.queryForObject("SELECT group_code FROM team WHERE id = 1001", String.class);
    assertThat(groupOfOne).isEqualTo("A");
    assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM match", Long.class)).isEqualTo(1L);
    String matchGroupCode =
        jdbc.queryForObject("SELECT group_code FROM match WHERE id = 5001", String.class);
    assertThat(matchGroupCode).isEqualTo("A");
  }
}
