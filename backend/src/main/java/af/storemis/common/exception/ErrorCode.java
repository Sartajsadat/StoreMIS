package af.storemis.common.exception;

import org.springframework.http.HttpStatus;

/** Stable, language-neutral error codes. Each has a matching {@code errors.<CODE>} key in the frontend translations. */
public enum ErrorCode {
    GENERIC(HttpStatus.INTERNAL_SERVER_ERROR),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    OPTIMISTIC_LOCK(HttpStatus.CONFLICT),
    DUPLICATE(HttpStatus.CONFLICT),

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),
    SESSION_EXPIRED(HttpStatus.UNAUTHORIZED),
    PASSWORD_INCORRECT(HttpStatus.UNPROCESSABLE_ENTITY),
    PASSWORD_WEAK(HttpStatus.UNPROCESSABLE_ENTITY),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
    CANNOT_MODIFY_SELF(HttpStatus.UNPROCESSABLE_ENTITY),
    LAST_ADMIN(HttpStatus.UNPROCESSABLE_ENTITY),

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE),
    FILE_TYPE_NOT_ALLOWED(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    STOCK_INSUFFICIENT(HttpStatus.CONFLICT),
    CASH_INSUFFICIENT(HttpStatus.CONFLICT),
    DAY_CLOSED(HttpStatus.CONFLICT);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
