package af.storemis.auth;

import static af.storemis.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import af.storemis.support.ApiClient;
import af.storemis.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc, json);
    }

    @Test
    void bootstrapAdminMustChangeTemporaryPasswordBeforeUsingTheApi() throws Exception {
        String admin = api.adminToken(); // performs the forced change on first run
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"))
                .andExpect(jsonPath("$.data.mustChangePassword").value(false))
                .andExpect(jsonPath("$.data.permissions.length()").value(9));
    }

    @Test
    void temporaryPasswordBlocksEverythingExceptPasswordChange() throws Exception {
        String admin = api.adminToken();
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "temp.user", "fullName", "Temp User", "role", "ENTRY_USER", "password", "Temp12345"))))
                .andExpect(status().isCreated());
        ApiClient.Login login = api.login("temp.user", "Temp12345");
        assertThat(login.user().get("mustChangePassword").asBoolean()).isTrue();

        mvc.perform(get("/api/v1/currencies").header("Authorization", bearer(login.accessToken()))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", bearer(login.accessToken())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
    }

    @Test
    void wrongPasswordIsRejectedWithoutRevealingWhichFieldWasWrong() throws Exception {
        api.adminToken();
        MvcResult unknown = api.loginRaw("nobody-here", "whatever1");
        MvcResult wrong = api.loginRaw("admin", "wrong-password1");
        assertThat(unknown.getResponse().getStatus()).isEqualTo(401);
        assertThat(wrong.getResponse().getStatus()).isEqualTo(401);
        assertThat(code(unknown)).isEqualTo("INVALID_CREDENTIALS");
        assertThat(code(wrong)).isEqualTo("INVALID_CREDENTIALS");
        Integer failedLogged = jdbc.queryForObject("select count(*) from audit_logs where action = 'LOGIN_FAILED'", Integer.class);
        assertThat(failedLogged).isGreaterThanOrEqualTo(2);
    }

    @Test
    void fiveFailedAttemptsLockTheAccount() throws Exception {
        String admin = api.adminToken();
        api.createReadyUser(admin, "lock.me", "ENTRY_USER", List.of());
        for (int i = 0; i < 4; i++) {
            assertThat(code(api.loginRaw("lock.me", "bad-pass" + i))).isEqualTo("INVALID_CREDENTIALS");
        }
        MvcResult fifth = api.loginRaw("lock.me", "bad-pass5");
        assertThat(code(fifth)).isEqualTo("ACCOUNT_LOCKED");
        // Even the correct password is refused while locked.
        assertThat(code(api.loginRaw("lock.me", "Ready12345"))).isEqualTo("ACCOUNT_LOCKED");
    }

    @Test
    void refreshRotatesTheCookieAndReuseRevokesAllSessions() throws Exception {
        String admin = api.adminToken();
        api.createReadyUser(admin, "rotate.me", "ENTRY_USER", List.of());
        ApiClient.Login login = api.login("rotate.me", "Ready12345");
        Cookie first = login.refreshCookie();
        assertThat(first).isNotNull();
        assertThat(first.isHttpOnly()).isTrue();

        MvcResult refreshed = mvc.perform(post("/api/v1/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("rotate.me"))
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("storemis_rt");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // Replaying the old token is treated as theft: rejected, and the newer token dies too.
        mvc.perform(post("/api/v1/auth/refresh").cookie(first)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"));
        mvc.perform(post("/api/v1/auth/refresh").cookie(second)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookie() throws Exception {
        String admin = api.adminToken();
        api.createReadyUser(admin, "logout.me", "ENTRY_USER", List.of());
        ApiClient.Login login = api.login("logout.me", "Ready12345");
        mvc.perform(post("/api/v1/auth/logout").cookie(login.refreshCookie()))
                .andExpect(status().isOk())
                .andExpect(cookie().maxAge("storemis_rt", 0));
        mvc.perform(post("/api/v1/auth/refresh").cookie(login.refreshCookie())).andExpect(status().isUnauthorized());
    }

    @Test
    void deactivatedUserLosesAccessImmediately() throws Exception {
        String admin = api.adminToken();
        String token = api.createReadyUser(admin, "fire.me", "ENTRY_USER", List.of());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token))).andExpect(status().isOk());
        Long id = jdbc.queryForObject("select id from users where username = 'fire.me'", Long.class);
        mvc.perform(patch("/api/v1/users/" + id + "/status").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}")).andExpect(status().isOk());
        // The access token is still unexpired, but the user is re-checked on every request.
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token))).andExpect(status().isUnauthorized());
        assertThat(code(api.loginRaw("fire.me", "Ready12345"))).isEqualTo("ACCOUNT_DISABLED");
    }

    @Test
    void protectedEndpointsRequireAToken() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer not-a-jwt")).andExpect(status().isUnauthorized());
    }

    @Test
    void changePasswordEnforcesPolicyAndCurrentPassword() throws Exception {
        String admin = api.adminToken();
        String token = api.createReadyUser(admin, "policy.me", "ENTRY_USER", List.of());
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong-one1\",\"newPassword\":\"Another123\"}"))
                .andExpect(jsonPath("$.error.code").value("PASSWORD_INCORRECT"));
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"Ready12345\",\"newPassword\":\"onlyletters\"}"))
                .andExpect(jsonPath("$.error.code").value("PASSWORD_WEAK"));
    }

    private String code(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).path("error").path("code").asText();
    }
}
