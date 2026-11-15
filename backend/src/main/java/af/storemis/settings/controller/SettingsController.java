package af.storemis.settings.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.settings.dto.BusinessSettingsResponse;
import af.storemis.settings.dto.CurrencyResponse;
import af.storemis.settings.dto.PublicSettingsResponse;
import af.storemis.settings.dto.UpdateBusinessSettingsRequest;
import af.storemis.settings.service.SettingsService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class SettingsController {

    private final SettingsService settings;

    public SettingsController(SettingsService settings) {
        this.settings = settings;
    }

    /** Unauthenticated — used by the login screen. */
    @GetMapping("/settings/public")
    public ApiResponse<PublicSettingsResponse> publicSettings() {
        return ApiResponse.ok(settings.getPublic());
    }

    @GetMapping("/settings/business")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BusinessSettingsResponse> get() {
        return ApiResponse.ok(settings.get());
    }

    @PutMapping("/settings/business")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BusinessSettingsResponse> update(@Valid @RequestBody UpdateBusinessSettingsRequest request) {
        return ApiResponse.ok(settings.update(request));
    }

    @PostMapping(value = "/settings/business/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BusinessSettingsResponse> uploadLogo(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(settings.uploadLogo(file));
    }

    @DeleteMapping("/settings/business/logo")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<BusinessSettingsResponse> removeLogo() {
        return ApiResponse.ok(settings.removeLogo());
    }

    @GetMapping("/currencies")
    public ApiResponse<List<CurrencyResponse>> currencies() {
        return ApiResponse.ok(settings.currencies());
    }
}
