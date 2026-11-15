package af.storemis.user.controller;

import af.storemis.common.api.ApiResponse;
import af.storemis.common.security.SecurityUtils;
import af.storemis.user.dto.PreferencesDto;
import af.storemis.user.service.PreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own UI preferences (language, theme, sidebar). */
@RestController
@RequestMapping("/api/v1/me/preferences")
public class PreferenceController {

    private final PreferenceService preferences;

    public PreferenceController(PreferenceService preferences) {
        this.preferences = preferences;
    }

    @GetMapping
    public ApiResponse<PreferencesDto> get() {
        return ApiResponse.ok(preferences.get(SecurityUtils.requirePrincipal().id()));
    }

    @PutMapping
    public ApiResponse<PreferencesDto> update(@Valid @RequestBody PreferencesDto dto) {
        return ApiResponse.ok(preferences.update(SecurityUtils.requirePrincipal().id(), dto));
    }
}
