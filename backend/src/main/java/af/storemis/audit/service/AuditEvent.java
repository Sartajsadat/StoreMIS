package af.storemis.audit.service;

import af.storemis.audit.entity.AuditAction;
import af.storemis.audit.entity.AuditModule;
import java.util.Map;

/**
 * What happened. {@code description} is a short, language-neutral English summary for the log;
 * the UI shows translated action/module names and the reference.
 */
public record AuditEvent(AuditAction action, AuditModule module, String entityType, Long entityId, String reference, String description,
        Map<String, Object> details) {

    public static AuditEvent of(AuditAction action, AuditModule module, String entityType, Long entityId, String reference, String description) {
        return new AuditEvent(action, module, entityType, entityId, reference, description, null);
    }

    public AuditEvent withDetails(Map<String, Object> details) {
        return new AuditEvent(action, module, entityType, entityId, reference, description, details);
    }
}
