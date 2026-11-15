package af.storemis.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Integration tests run against a real PostgreSQL 16 with all Flyway migrations applied.
 * One container is shared by the whole test run. Set STOREMIS_TEST_JDBC_URL (with _USER/_PASSWORD)
 * to use an existing database instead of Docker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTest {

    private static final String EXTERNAL_URL = System.getenv("STOREMIS_TEST_JDBC_URL");
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        if (EXTERNAL_URL == null) {
            POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("storemis").withUsername("storemis").withPassword("storemis");
            POSTGRES.start();
        } else {
            POSTGRES = null;
        }
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        if (POSTGRES != null) {
            registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            registry.add("spring.datasource.username", POSTGRES::getUsername);
            registry.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            registry.add("spring.datasource.url", () -> EXTERNAL_URL);
            registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("STOREMIS_TEST_JDBC_USER", "storemis"));
            registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("STOREMIS_TEST_JDBC_PASSWORD", "storemis"));
        }
    }
}
