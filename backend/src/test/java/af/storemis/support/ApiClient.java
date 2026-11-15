package af.storemis.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Small helper for signing in from tests. */
public final class ApiClient {

    public static final String ADMIN = "admin";
    public static final String ADMIN_INITIAL_PASSWORD = "Admin12345";
    public static final String ADMIN_PASSWORD = "Admin67890";

    public record Login(String accessToken, Cookie refreshCookie, JsonNode user) {}

    private final MockMvc mvc;
    private final ObjectMapper json;

    public ApiClient(MockMvc mvc, ObjectMapper json) {
        this.mvc = mvc;
        this.json = json;
    }

    public MvcResult loginRaw(String username, String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password)))).andReturn();
    }

    public Login login(String username, String password) throws Exception {
        MvcResult r = loginRaw(username, password);
        if (r.getResponse().getStatus() != 200) {
            throw new AssertionError("Login failed for " + username + ": " + r.getResponse().getContentAsString());
        }
        JsonNode data = json.readTree(r.getResponse().getContentAsString()).get("data");
        return new Login(data.get("accessToken").asText(), r.getResponse().getCookie("storemis_rt"), data.get("user"));
    }

    /** Signs in as the bootstrap admin, completing the forced password change the first time. */
    public synchronized String adminToken() throws Exception {
        MvcResult r = loginRaw(ADMIN, ADMIN_PASSWORD);
        if (r.getResponse().getStatus() == 200) {
            return json.readTree(r.getResponse().getContentAsString()).get("data").get("accessToken").asText();
        }
        Login first = login(ADMIN, ADMIN_INITIAL_PASSWORD);
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", "Bearer " + first.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", ADMIN_INITIAL_PASSWORD, "newPassword", ADMIN_PASSWORD))))
                .andExpect(status().isOk());
        return login(ADMIN, ADMIN_PASSWORD).accessToken();
    }

    /** Creates a user as admin and completes their first password change; returns a ready access token. */
    public String createReadyUser(String adminToken, String username, String role, java.util.List<String> permissions) throws Exception {
        Map<String, Object> body = new java.util.HashMap<>(Map.of("username", username, "fullName", "Test " + username, "role", role, "password", "Temp12345"));
        if (permissions != null) body.put("permissions", permissions);
        mvc.perform(post("/api/v1/users").header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body))).andExpect(status().isCreated());
        Login first = login(username, "Temp12345");
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization", bearer(first.accessToken())).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", "Temp12345", "newPassword", "Ready12345")))).andExpect(status().isOk());
        return login(username, "Ready12345").accessToken();
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }
}
