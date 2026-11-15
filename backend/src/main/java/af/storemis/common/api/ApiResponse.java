package af.storemis.common.api;

import java.time.OffsetDateTime;

/** Standard success envelope: {@code { success, data, message, timestamp }}. */
public record ApiResponse<T>(boolean success, T data, String message, OffsetDateTime timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, OffsetDateTime.now());
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null, OffsetDateTime.now());
    }
}
