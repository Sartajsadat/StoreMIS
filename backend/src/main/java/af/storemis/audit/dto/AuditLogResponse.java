package af.storemis.audit.dto;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditLog;
import af.storemis.audit.entity.AuditModule;
import java.time.OffsetDateTime;
import java.util.Map;

public record AuditLogResponse(Long id, OffsetDateTime occurredAt, Long userId, String username, AuditAction action, AuditModule module,
        String entityType, Long entityId, String reference, String description, Map<String, Object> details, String ipAddress) {

    public static AuditLogResponse from(AuditLog l) {
        return new AuditLogResponse(l.getId(), l.getOccurredAt(), l.getUserId(), l.getUsername(), l.getAction(), l.getModule(),
                l.getEntityType(), l.getEntityId(), l.getReference(), l.getDescription(), l.getDetails(), l.getIpAddress());
    }
}
