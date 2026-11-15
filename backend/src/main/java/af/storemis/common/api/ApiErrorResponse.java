package af.storemis.common.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

/**
 * Error envelope. The frontend translates {@code error.code} (e.g. STOCK_INSUFFICIENT) with {@code params};
 * the backend never returns translated text.
 */
public record ApiErrorResponse(boolean success, ErrorBody error, OffsetDateTime timestamp) {

    public record ErrorBody(String code, Map<String, Object> params, List<FieldErrorDto> fieldErrors) {}

    public record FieldErrorDto(String field, String code, Map<String, Object> params) {}

    public static ApiErrorResponse of(String code, Map<String, Object> params, List<FieldErrorDto> fieldErrors) {
        return new ApiErrorResponse(false, new ErrorBody(code, params == null ? Map.of() : params,
                fieldErrors == null ? List.of() : fieldErrors), OffsetDateTime.now());
    }
}
