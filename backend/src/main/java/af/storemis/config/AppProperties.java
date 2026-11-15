package af.storemis.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "storemis")
public record AppProperties(String timezone, Jwt jwt, Cookie cookie, Security security, BootstrapAdmin bootstrapAdmin, Files files) {

    public record Jwt(String secret, Duration accessTokenTtl, Duration refreshTokenTtl, String issuer) {}

    public record Cookie(String name, String path, boolean secure) {}

    public record Security(int maxFailedAttempts, int lockMinutes) {}

    public record BootstrapAdmin(String username, String password, String fullName) {}

    public record Files(String dir) {}
}
