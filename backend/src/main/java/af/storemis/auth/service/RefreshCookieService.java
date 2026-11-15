package af.storemis.auth.service;

import af.storemis.config.AppProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** The refresh token travels only in an httpOnly, SameSite=Strict cookie scoped to the auth endpoints. */
@Component
public class RefreshCookieService {

    private final AppProperties.Cookie cookie;
    private final Duration ttl;

    public RefreshCookieService(AppProperties properties) {
        this.cookie = properties.cookie();
        this.ttl = properties.jwt().refreshTokenTtl();
    }

    public void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, build(token, ttl).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, build("", Duration.ZERO).toString());
    }

    public Optional<String> read(HttpServletRequest request) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies()).filter(c -> cookie.name().equals(c.getName())).map(Cookie::getValue)
                .filter(v -> v != null && !v.isBlank()).findFirst();
    }

    private ResponseCookie build(String value, Duration maxAge) {
        return ResponseCookie.from(cookie.name(), value).httpOnly(true).secure(cookie.secure()).sameSite("Strict").path(cookie.path())
                .maxAge(maxAge).build();
    }
}
