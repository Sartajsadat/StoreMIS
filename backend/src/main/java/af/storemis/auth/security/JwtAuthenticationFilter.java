package af.storemis.auth.security;

import af.storemis.auth.service.JwtService;
import af.storemis.common.api.ApiErrorResponse;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.security.AppPrincipal;
import af.storemis.user.entity.AppUser;
import af.storemis.user.repository.AppUserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates "Bearer" requests. The user is re-loaded on every request, so deactivation and
 * role/permission changes take effect immediately. Users holding a temporary password may only
 * reach the endpoints needed to change it.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> ALLOWED_WITH_TEMPORARY_PASSWORD = Set.of(
            "/api/v1/auth/me", "/api/v1/auth/change-password", "/api/v1/auth/logout", "/api/v1/auth/refresh",
            "/api/v1/me/preferences", "/api/v1/settings/public", "/api/v1/currencies");

    private final JwtService jwt;
    private final AppUserRepository users;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtService jwt, AppUserRepository users, ObjectMapper objectMapper) {
        this.jwt = jwt;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            AppUser user = jwt.parseUserId(header.substring(7).trim()).flatMap(users::findById).filter(AppUser::isActive).orElse(null);
            if (user != null) {
                if (user.isMustChangePassword() && !ALLOWED_WITH_TEMPORARY_PASSWORD.contains(request.getRequestURI())) {
                    writeError(response, ErrorCode.PASSWORD_CHANGE_REQUIRED);
                    return;
                }
                AppPrincipal principal = new AppPrincipal(user.getId(), user.getUsername(), user.getRole(), user.getEffectivePermissions());
                List<GrantedAuthority> authorities = new ArrayList<>();
                authorities.add(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
                principal.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority("PERM_" + p.name())));
                var auth = new UsernamePasswordAuthenticationToken(principal, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        }
        chain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(code.name(), Map.of(), null));
    }
}
