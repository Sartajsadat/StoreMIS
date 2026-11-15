package af.storemis.auth.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import af.storemis.audit.service.AuditEvent;
import af.storemis.audit.service.AuditService;
import af.storemis.auth.dto.AuthResponse;
import af.storemis.auth.entity.RefreshToken;
import af.storemis.auth.repository.RefreshTokenRepository;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.time.BusinessClock;
import af.storemis.common.web.ClientInfo;
import af.storemis.config.AppProperties;
import af.storemis.user.entity.AppUser;
import af.storemis.user.repository.AppUserRepository;
import af.storemis.user.service.CurrentUserService;
import af.storemis.user.service.PasswordPolicy;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    // Compared against when the username does not exist, so response time does not reveal valid usernames.
    private static final String DUMMY_HASH = "$2a$12$C6UzMDM.H6dfI/f/IKcEeO7ygmY6Yi0uIbiLR6bT0pQhCUKsXJl8e";

    /** Result of a successful login or refresh: the API response plus the new refresh token for the cookie. */
    public record Session(AuthResponse response, String refreshToken) {}

    private final AppUserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwt;
    private final LoginAttemptService attempts;
    private final CurrentUserService currentUser;
    private final AuditService audit;
    private final BusinessClock clock;
    private final Duration refreshTtl;

    public AuthService(AppUserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder, JwtService jwt,
            LoginAttemptService attempts, CurrentUserService currentUser, AuditService audit, BusinessClock clock, AppProperties properties) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
        this.refreshTtl = properties.jwt().refreshTokenTtl();
    }

    @Transactional
    public Session login(String username, String password) {
        OffsetDateTime now = clock.now();
        Optional<AppUser> found = users.findByUsernameIgnoreCase(username.trim());
        if (found.isEmpty()) {
            passwordEncoder.matches(password, DUMMY_HASH);
            audit.logIndependent(null, truncate(username.trim(), 50),
                    AuditEvent.of(AuditAction.LOGIN_FAILED, AuditModule.AUTH, "User", null, null, "Unknown username"));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        AppUser user = found.get();
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, ChronoUnit.MINUTES.between(now, user.getLockedUntil()) + 1);
            throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, Map.of("minutes", minutes));
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            boolean locked = attempts.recordFailure(user.getId());
            audit.logIndependent(user.getId(), user.getUsername(),
                    AuditEvent.of(AuditAction.LOGIN_FAILED, AuditModule.AUTH, "User", user.getId(), user.getUsername(),
                            locked ? "Wrong password; account locked" : "Wrong password"));
            if (locked) throw new BusinessException(ErrorCode.ACCOUNT_LOCKED, Map.of("minutes", attempts.lockMinutes()));
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }
        // Only reveal that the account is disabled after the correct password was given.
        if (!user.isActive()) {
            audit.logIndependent(user.getId(), user.getUsername(),
                    AuditEvent.of(AuditAction.LOGIN_FAILED, AuditModule.AUTH, "User", user.getId(), user.getUsername(), "Account disabled"));
            throw new BusinessException(ErrorCode.ACCOUNT_DISABLED);
        }
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        audit.logAs(user.getId(), user.getUsername(), AuditEvent.of(AuditAction.LOGIN, AuditModule.AUTH, "User", user.getId(), user.getUsername(), "Signed in"));
        return startSession(user, now);
    }

    /**
     * Rotates the refresh token. Presenting an already-rotated token means it was stolen or replayed:
     * every session of that user is revoked.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public Session refresh(String presentedToken) {
        OffsetDateTime now = clock.now();
        RefreshToken token = refreshTokens.findByTokenHash(TokenHasher.sha256(presentedToken))
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
        if (token.getRevokedAt() != null) {
            log.warn("Refresh token reuse detected for user {}; revoking all sessions", token.getUserId());
            refreshTokens.revokeAllForUser(token.getUserId(), now);
            throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        }
        if (!token.isActive(now)) throw new BusinessException(ErrorCode.SESSION_EXPIRED);
        AppUser user = users.findById(token.getUserId()).filter(AppUser::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
        Session session = startSession(user, now);
        RefreshToken next = refreshTokens.findByTokenHash(TokenHasher.sha256(session.refreshToken())).orElseThrow();
        token.revoke(now, next.getId());
        return session;
    }

    @Transactional
    public void logout(String presentedToken) {
        if (presentedToken == null) return;
        OffsetDateTime now = clock.now();
        refreshTokens.findByTokenHash(TokenHasher.sha256(presentedToken)).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.revoke(now, null);
                users.findById(t.getUserId()).ifPresent(u -> audit.logAs(u.getId(), u.getUsername(),
                        AuditEvent.of(AuditAction.LOGOUT, AuditModule.AUTH, "User", u.getId(), u.getUsername(), "Signed out")));
            }
        });
    }

    /** Changes the user's own password and ends their other sessions (the current one stays signed in). */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword, String presentedRefreshToken) {
        AppUser user = users.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_INCORRECT);
        }
        PasswordPolicy.check(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WEAK, Map.of("reason", "same"));
        }
        OffsetDateTime now = clock.now();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(now);
        Optional<RefreshToken> current = presentedRefreshToken == null ? Optional.empty()
                : refreshTokens.findByTokenHash(TokenHasher.sha256(presentedRefreshToken));
        if (current.isPresent()) refreshTokens.revokeAllForUserExcept(userId, current.get().getId(), now);
        else refreshTokens.revokeAllForUser(userId, now);
        audit.log(AuditEvent.of(AuditAction.PASSWORD_CHANGE, AuditModule.AUTH, "User", userId, user.getUsername(), "Password changed"));
    }

    private Session startSession(AppUser user, OffsetDateTime now) {
        String raw = TokenHasher.newToken();
        refreshTokens.save(new RefreshToken(user.getId(), TokenHasher.sha256(raw), now, now.plus(refreshTtl), ClientInfo.ip(), ClientInfo.userAgent()));
        String access = jwt.issueAccessToken(user.getId(), user.getUsername());
        return new Session(new AuthResponse(access, jwt.accessTokenTtlSeconds(), currentUser.describe(user)), raw);
    }

    /** Housekeeping: expired refresh tokens are useless after a week. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kabul")
    @Transactional
    public void purgeExpiredTokens() {
        int n = refreshTokens.deleteExpiredBefore(clock.now().minusDays(7));
        if (n > 0) log.info("Purged {} expired refresh tokens", n);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
