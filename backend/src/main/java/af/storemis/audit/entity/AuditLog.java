package af.storemis.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Append-only activity record. There are no setters after construction and no update paths. */
@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "username", updatable = false)
    private String username;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, updatable = false)
    private AuditModule module;

    @Column(name = "entity_type", updatable = false)
    private String entityType;

    @Column(name = "entity_id", updatable = false)
    private Long entityId;

    @Column(name = "reference", updatable = false)
    private String reference;

    @Column(name = "description", updatable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", columnDefinition = "jsonb", updatable = false)
    private Map<String, Object> details;

    @Column(name = "ip_address", updatable = false)
    private String ipAddress;

    protected AuditLog() {}

    public AuditLog(OffsetDateTime occurredAt, Long userId, String username, AuditAction action, AuditModule module, String entityType,
            Long entityId, String reference, String description, Map<String, Object> details, String ipAddress) {
        this.occurredAt = occurredAt;
        this.userId = userId;
        this.username = username;
        this.action = action;
        this.module = module;
        this.entityType = entityType;
        this.entityId = entityId;
        this.reference = reference;
        this.description = description;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public Long getId() {
        return id;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public Long getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditModule getModule() {
        return module;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public String getIpAddress() {
        return ipAddress;
    }
}
