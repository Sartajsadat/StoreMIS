package af.storemis.user.entity;

import af.storemis.common.security.Permission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "permissions")
public class PermissionEntity {

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "code")
    private Permission code;

    @Column(name = "description", nullable = false)
    private String description;

    @Column(name = "default_for_entry_user", nullable = false)
    private boolean defaultForEntryUser;

    public Permission getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public boolean isDefaultForEntryUser() {
        return defaultForEntryUser;
    }
}
