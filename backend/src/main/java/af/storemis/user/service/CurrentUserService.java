package af.storemis.user.service;

import af.storemis.user.dto.CurrentUserResponse;
import af.storemis.common.exception.NotFoundException;
import af.storemis.user.entity.AppUser;
import af.storemis.user.repository.AppUserRepository;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentUserService {

    private final PreferenceService preferences;
    private final AppUserRepository users;

    public CurrentUserService(PreferenceService preferences, AppUserRepository users) {
        this.preferences = preferences;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse describe(Long userId) {
        return describe(users.findById(userId).orElseThrow(() -> new NotFoundException("User", userId)));
    }

    @Transactional(readOnly = true)
    public CurrentUserResponse describe(AppUser u) {
        return new CurrentUserResponse(u.getId(), u.getUsername(), u.getFullName(), u.getPhone(), u.getRole(),
                new TreeSet<>(u.getEffectivePermissions()), u.isMustChangePassword(), preferences.get(u.getId()));
    }
}
