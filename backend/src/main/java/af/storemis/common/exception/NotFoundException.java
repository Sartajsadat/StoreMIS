package af.storemis.common.exception;

import java.util.Map;

public class NotFoundException extends BusinessException {

    public NotFoundException(String entity, Object id) {
        super(ErrorCode.NOT_FOUND, Map.of("entity", entity, "id", String.valueOf(id)));
    }
}
