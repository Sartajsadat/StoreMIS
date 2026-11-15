package af.storemis.settings;

import static af.storemis.support.ApiClient.bearer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import af.storemis.support.ApiClient;
import af.storemis.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

class SettingsIntegrationTest extends IntegrationTest {

    // 1x1 transparent PNG
    private static final byte[] PNG = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = new ApiClient(mvc, json).adminToken();
    }

    @Test
    void publicSettingsNeedNoLogin() throws Exception {
        mvc.perform(get("/api/v1/settings/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseCurrency").value("AFN"))
                .andExpect(jsonPath("$.data.currencies.length()").value(2));
    }

    @Test
    void adminUpdatesBusinessSettingsWithVersionCheck() throws Exception {
        JsonNode current = json.readTree(mvc.perform(get("/api/v1/settings/business").header("Authorization", bearer(admin)))
                .andReturn().getResponse().getContentAsString()).get("data");
        ObjectNode body = (ObjectNode) current.deepCopy();
        body.put("businessName", "Kabul Electric Store");
        body.put("defaultLanguage", "fa");
        body.put("phone", "+93 700 123 456");
        mvc.perform(put("/api/v1/settings/business").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.businessName").value("Kabul Electric Store"));
        mvc.perform(get("/api/v1/settings/public")).andExpect(jsonPath("$.data.defaultLanguage").value("fa"));
        // Same (now stale) version again
        mvc.perform(put("/api/v1/settings/business").header("Authorization", bearer(admin)).contentType(MediaType.APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isConflict());
    }

    @Test
    void logoUploadAcceptsImagesOnlyAndIsServedPublicly() throws Exception {
        MockMultipartFile fake = new MockMultipartFile("file", "logo.png", "image/png", "<script>alert(1)</script>".getBytes());
        mvc.perform(multipart("/api/v1/settings/business/logo").file(fake).header("Authorization", bearer(admin)))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("FILE_TYPE_NOT_ALLOWED"));

        MockMultipartFile png = new MockMultipartFile("file", "anything.bin", "application/octet-stream", PNG);
        String logoUrl = json.readTree(mvc.perform(multipart("/api/v1/settings/business/logo").file(png).header("Authorization", bearer(admin)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).at("/data/logoUrl").asText();
        mvc.perform(get(logoUrl)).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        mvc.perform(get("/api/v1/files/public/../../etc/passwd")).andExpect(status().is4xxClientError());
    }
}
