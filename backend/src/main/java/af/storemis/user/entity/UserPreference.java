package af.storemis.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "user_preferences")
public class UserPreference {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "language", nullable = false, length = 5)
    private String language = "en";

    @Column(name = "theme", nullable = false, length = 10)
    private String theme = "light";

    @Column(name = "sidebar_collapsed", nullable = false)
    private boolean sidebarCollapsed;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    protected UserPreference() {}

    public UserPreference(Long userId, String language, String theme) {
        this.userId = userId;
        this.language = language;
        this.theme = theme;
    }

    public Long getUserId() {
        return userId;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTheme() {
        return theme;
    }

    public void setTheme(String theme) {
        this.theme = theme;
    }

    public boolean isSidebarCollapsed() {
        return sidebarCollapsed;
    }

    public void setSidebarCollapsed(boolean sidebarCollapsed) {
        this.sidebarCollapsed = sidebarCollapsed;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
