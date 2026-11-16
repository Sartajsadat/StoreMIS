package af.storemis.user.service;

import af.storemis.user.entity.AppUser;
import af.storemis.user.repository.AppUserRepository;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves user ids to display names for other modules ("created by" columns). */
@Service
public class UserDirectory {

    private final AppUserRepository users;

    public UserDirectory(AppUserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        var wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (wanted.isEmpty()) return map;
        for (AppUser u : users.findAllById(wanted)) map.put(u.getId(), u.getFullName());
        return map;
    }
}
