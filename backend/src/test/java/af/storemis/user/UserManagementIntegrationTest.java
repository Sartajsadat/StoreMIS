package af.storemis.user;

import static af.storemis.support.ApiClient.bearer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import af.storemis.support.ApiClient;
import af.storemis.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

class UserManagementIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    ApiClient api;
    String admin;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc, json);
        admin = api.adminToken();
    }

    @Test
    void newEntryUserGetsDefaultPermissionsAndMustChangePassword() throws Exception {
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "cashier.one", "fullName", "Cashier One", "role", "ENTRY_USER", "password", "Start12345"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true))
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.permissions", containsInAnyOrder("RECEIVE_CUSTOMER_PAYMENTS", "GIVE_DISCOUNT")));
        Integer audits = jdbc.queryForObject("select count(*) from audit_logs where module = 'USERS' and action = 'CREATE' and reference = 'cashier.one'", Integer.class);
        assertThat(audits).isEqualTo(1);
    }

    @Test
    void usernamesAreUniqueIgnoringCase() throws Exception {
        api.createReadyUser(admin, "Unique.Name", "ENTRY_USER", List.of());
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", "unique.name", "fullName", "Dup", "role", "ENTRY_USER", "password", "Start12345"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("DUPLICATE"))
                .andExpect(jsonPath("$.error.params.field").value("username"));
    }

    @Test
    void validationErrorsUseTranslatableCodes() throws Exception {
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"ab\",\"fullName\":\"\",\"role\":\"ENTRY_USER\",\"password\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'username')].code").value("minLength"))
                .andExpect(jsonPath("$.error.fieldErrors[?(@.field == 'fullName')].code").value(org.hamcrest.Matchers.hasItem("required")));
    }

    @Test
    void entryUsersCannotManageUsersOrReadAuditLogs() throws Exception {
        String cashier = api.createReadyUser(admin, "no.admin", "ENTRY_USER", List.of());
        mvc.perform(get("/api/v1/users").header("Authorization", bearer(cashier))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/audit-logs").header("Authorization", bearer(cashier))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/settings/business").header("Authorization", bearer(cashier))).andExpect(status().isForbidden());
    }

    @Test
    void permissionChangesApplyImmediatelyAndAreAudited() throws Exception {
        String token = api.createReadyUser(admin, "perm.change", "ENTRY_USER", List.of());
        JsonNode user = json.readTree(mvc.perform(get("/api/v1/users?q=perm.change").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString()).at("/data/content/0");
        mvc.perform(put("/api/v1/users/" + user.get("id").asLong()).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("fullName", "Perm Change", "role", "ENTRY_USER", "permissions", List.of("RECORD_EXPENSES"),
                        "version", user.get("version").asInt()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions[0]").value("RECORD_EXPENSES"));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.permissions[0]").value("RECORD_EXPENSES"));
        String details = jdbc.queryForObject("select details::text from audit_logs where action = 'UPDATE' and reference = 'perm.change' order by id desc limit 1", String.class);
        assertThat(details).contains("RECORD_EXPENSES").contains("fullName");
    }

    @Test
    void staleUpdatesAreRejected() throws Exception {
        api.createReadyUser(admin, "stale.edit", "ENTRY_USER", List.of());
        JsonNode user = json.readTree(mvc.perform(get("/api/v1/users?q=stale.edit").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString()).at("/data/content/0");
        String body = json.writeValueAsString(Map.of("fullName", "First Edit", "role", "ENTRY_USER", "permissions", List.of(), "version", user.get("version").asInt()));
        mvc.perform(put("/api/v1/users/" + user.get("id").asLong()).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mvc.perform(put("/api/v1/users/" + user.get("id").asLong()).header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("OPTIMISTIC_LOCK"));
    }

    @Test
    void adminCannotDeactivateThemselvesOrRemoveTheLastAdmin() throws Exception {
        Long adminId = jdbc.queryForObject("select id from users where username = 'admin'", Long.class);
        mvc.perform(patch("/api/v1/users/" + adminId + "/status").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\": false}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CANNOT_MODIFY_SELF"));
    }

    @Test
    void resetPasswordForcesChangeAndUnlocks() throws Exception {
        api.createReadyUser(admin, "reset.me", "ENTRY_USER", List.of());
        Long id = jdbc.queryForObject("select id from users where username = 'reset.me'", Long.class);
        mvc.perform(post("/api/v1/users/" + id + "/reset-password").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"Reset12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustChangePassword").value(true));
        assertThat(api.login("reset.me", "Reset12345").user().get("mustChangePassword").asBoolean()).isTrue();
        assertThat(api.loginRaw("reset.me", "Ready12345").getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void preferencesArePerUser() throws Exception {
        String token = api.createReadyUser(admin, "prefs.user", "ENTRY_USER", List.of());
        mvc.perform(put("/api/v1/me/preferences").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"ps\",\"theme\":\"dark\",\"sidebarCollapsed\":true}")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(jsonPath("$.data.preferences.language").value("ps"))
                .andExpect(jsonPath("$.data.preferences.theme").value("dark"));
        mvc.perform(put("/api/v1/me/preferences").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"language\":\"de\",\"theme\":\"dark\",\"sidebarCollapsed\":true}")).andExpect(status().isBadRequest());
    }

    @Test
    void auditLogCanBeFilteredByModuleAndAction() throws Exception {
        mvc.perform(get("/api/v1/audit-logs?module=AUTH&action=LOGIN&size=5").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].module").value("AUTH"))
                .andExpect(jsonPath("$.data.content[0].action").value("LOGIN"))
                .andExpect(jsonPath("$.data.size").value(5));
    }
}
