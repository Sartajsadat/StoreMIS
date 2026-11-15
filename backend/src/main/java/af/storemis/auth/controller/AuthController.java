package af.storemis.auth.controller;

import af.storemis.auth.dto.AuthResponse;
import af.storemis.auth.dto.ChangePasswordRequest;
import af.storemis.auth.dto.LoginRequest;
import af.storemis.auth.service.AuthService;
import af.storemis.auth.service.RefreshCookieService;
import af.storemis.common.api.ApiResponse;
import af.storemis.common.exception.BusinessException;
import af.storemis.common.exception.ErrorCode;
import af.storemis.common.security.SecurityUtils;
import af.storemis.user.dto.CurrentUserResponse;
import af.storemis.user.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieService cookies;
    private final CurrentUserService currentUser;

    public AuthController(AuthService authService, RefreshCookieService cookies, CurrentUserService currentUser) {
        this.authService = authService;
        this.cookies = cookies;
        this.currentUser = currentUser;
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthService.Session session = authService.login(request.username(), request.password());
        cookies.write(response, session.refreshToken());
        return ApiResponse.ok(session.response());
    }

    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = cookies.read(request).orElseThrow(() -> new BusinessException(ErrorCode.SESSION_EXPIRED));
        try {
            AuthService.Session session = authService.refresh(token);
            cookies.write(response, session.refreshToken());
            return ApiResponse.ok(session.response());
        } catch (BusinessException e) {
            cookies.clear(response);
            throw e;
        }
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookies.read(request).orElse(null));
        cookies.clear(response);
        return ApiResponse.ok();
    }

    @GetMapping("/me")
    public ApiResponse<CurrentUserResponse> me() {
        return ApiResponse.ok(currentUser.describe(SecurityUtils.requirePrincipal().id()));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body, HttpServletRequest request) {
        authService.changePassword(SecurityUtils.requirePrincipal().id(), body.currentPassword(), body.newPassword(),
                cookies.read(request).orElse(null));
        return ApiResponse.ok();
    }
}
