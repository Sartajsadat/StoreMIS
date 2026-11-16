package af.storemis.config;

import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Refuses to start in production with the development token key or the default database password. */
@Component
@Profile("prod")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ProductionSafetyCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductionSafetyCheck.class);
    static final String DEV_SECRET = "dev-only-secret-change-me-dev-only-secret-change-me";

    private final String jwtSecret;
    private final String dbPassword;

    public ProductionSafetyCheck(@Value("${storemis.jwt.secret}") String jwtSecret, @Value("${spring.datasource.password}") String dbPassword) {
        this.jwtSecret = jwtSecret;
        this.dbPassword = dbPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        problems(jwtSecret, dbPassword).forEach(p -> log.error("Unsafe production setting: {}", p));
        if (!problems(jwtSecret, dbPassword).isEmpty()) {
            throw new IllegalStateException("Store MIS will not start in production with unsafe settings. See deploy/.env.example.");
        }
    }

    static java.util.List<String> problems(String jwtSecret, String dbPassword) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) out.add("JWT_SECRET must be at least 32 characters");
        else if (jwtSecret.equals(DEV_SECRET) || jwtSecret.startsWith("change-me")) out.add("JWT_SECRET is still the example value");
        if (dbPassword == null || dbPassword.length() < 8 || dbPassword.equals("storemis") || dbPassword.startsWith("change-me")) {
            out.add("DB_PASSWORD is missing, too short or still the example value");
        }
        return out;
    }
}
