package af.storemis.user.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.api.PageRequests;
import af.storemis.common.api.PageResponse;
import af.storemis.common.security.Role;
import af.storemis.common.time.BusinessClock;
import af.storemis.user.dto.CreateUserRequest;
import af.storemis.user.dto.PermissionResponse;
import af.storemis.user.dto.ResetPasswordRequest;
import af.storemis.user.dto.StatusRequest;
import af.storemis.user.dto.UpdateUserRequest;
import af.storemis.user.dto.UserResponse;
import af.storemis.user.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('ADMIN')")
public class UserController {

    private final UserService users;
    private final BusinessClock clock;

    public UserController(UserService users, BusinessClock clock) {
        this.users = users;
        this.clock = clock;
    }

    @GetMapping("/users")
    public ApiResponse<PageResponse<UserResponse>> list(@RequestParam(required = false) String q, @RequestParam(required = false) Role role,
            @RequestParam(required = false) Boolean active, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort) {
        var pageable = PageRequests.of(page, size, sort, Set.of("username", "fullName", "createdAt", "lastLoginAt"), Sort.by("fullName"));
        return ApiResponse.ok(PageResponse.of(users.search(q, role, active, pageable), u -> UserResponse.from(u, clock.now())));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<UserResponse> get(@PathVariable Long id) {
        return ApiResponse.ok(UserResponse.from(users.get(id), clock.now()));
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        return ApiResponse.ok(UserResponse.from(users.create(request), clock.now()));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<UserResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return ApiResponse.ok(UserResponse.from(users.update(id, request), clock.now()));
    }

    @PatchMapping("/users/{id}/status")
    public ApiResponse<UserResponse> status(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return ApiResponse.ok(UserResponse.from(users.setActive(id, request.active()), clock.now()));
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<UserResponse> resetPassword(@PathVariable Long id, @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(UserResponse.from(users.resetPassword(id, request.newPassword()), clock.now()));
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionResponse>> permissions() {
        return ApiResponse.ok(users.listPermissions());
    }
}
