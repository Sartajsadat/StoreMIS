package af.storemis.audit.entity;

/** Audit actions. The frontend translates them with the {@code audit.actions.<ACTION>} keys. */
public enum AuditAction {
    LOGIN,
    LOGIN_FAILED,
    LOGOUT,
    PASSWORD_CHANGE,
    PASSWORD_RESET,
    CREATE,
    UPDATE,
    ACTIVATE,
    DEACTIVATE,
    CONFIRM,
    CANCEL,
    REVERSE,
    VOID,
    PAYMENT,
    ADJUST,
    RETURN,
    CLOSE,
    REOPEN,
    SETTINGS_CHANGE,
    UPLOAD
}
