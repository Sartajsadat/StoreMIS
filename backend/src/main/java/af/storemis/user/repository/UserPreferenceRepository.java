package af.storemis.user.repository;

import af.storemis.user.entity.UserPreference;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {

    /** Atomic insert-or-update, so two simultaneous saves (e.g. a change plus the page-unload flush) never collide. */
    @Modifying
    @Query(value = """
            insert into user_preferences (user_id, language, theme, sidebar_collapsed, updated_at)
            values (:userId, :language, :theme, :sidebarCollapsed, :now)
            on conflict (user_id) do update set language = excluded.language, theme = excluded.theme,
                sidebar_collapsed = excluded.sidebar_collapsed, updated_at = excluded.updated_at""", nativeQuery = true)
    void upsert(Long userId, String language, String theme, boolean sidebarCollapsed, OffsetDateTime now);
}
