package af.storemis.common.exception;

import java.util.Map;

/** A business-rule failure carrying a translatable code and its parameters. */
public class BusinessException extends RuntimeException {

    private final ErrorCode code;
    private final transient Map<String, Object> params;

    public BusinessException(ErrorCode code) {
        this(code, Map.of());
    }

    public BusinessException(ErrorCode code, Map<String, Object> params) {
        super(code.name());
        this.code = code;
        this.params = params == null ? Map.of() : params;
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getParams() {
        return params;
    }
}
