package af.storemis.user.service;

import af.storemis.common.time.BusinessClock;
import af.storemis.settings.service.SettingsService;
import af.storemis.user.dto.PreferencesDto;
import af.storemis.user.repository.UserPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceService {

    private final UserPreferenceRepository repository;
    private final SettingsService settingsService;
    private final BusinessClock clock;

    public PreferenceService(UserPreferenceRepository repository, SettingsService settingsService, BusinessClock clock) {
        this.repository = repository;
        this.settingsService = settingsService;
        this.clock = clock;
    }

    /** Stored preferences, or the business defaults for a user who has never chosen. */
    @Transactional(readOnly = true)
    public PreferencesDto get(Long userId) {
        return repository.findById(userId).map(p -> new PreferencesDto(p.getLanguage(), p.getTheme(), p.isSidebarCollapsed()))
                .orElseGet(() -> {
                    var s = settingsService.getEntity();
                    return new PreferencesDto(s.getDefaultLanguage(), s.getDefaultTheme(), false);
                });
    }

    @Transactional
    public PreferencesDto update(Long userId, PreferencesDto dto) {
        repository.upsert(userId, dto.language(), dto.theme(), dto.sidebarCollapsed(), clock.now());
        return dto;
    }
}
