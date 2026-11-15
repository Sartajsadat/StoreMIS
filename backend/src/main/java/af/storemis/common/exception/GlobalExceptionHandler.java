package af.storemis.common.exception;

import af.storemis.common.api.ApiErrorResponse;
import af.storemis.common.api.ApiErrorResponse.FieldErrorDto;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Converts every exception into the standard error envelope with a translatable code. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiErrorResponse> business(BusinessException ex) {
        return build(ex.getCode(), ex.getParams(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> validation(MethodArgumentNotValidException ex) {
        List<FieldErrorDto> fields = ex.getBindingResult().getFieldErrors().stream().map(GlobalExceptionHandler::toFieldError).toList();
        return build(ErrorCode.VALIDATION_FAILED, Map.of(), fields);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> constraint(ConstraintViolationException ex) {
        List<FieldErrorDto> fields = ex.getConstraintViolations().stream()
                .map(v -> new FieldErrorDto(v.getPropertyPath().toString(), "invalid", Map.of()))
                .toList();
        return build(ErrorCode.VALIDATION_FAILED, Map.of(), fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ApiErrorResponse> badRequest(Exception ex) {
        return build(ErrorCode.VALIDATION_FAILED, Map.of(), null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> optimisticLock(ObjectOptimisticLockingFailureException ex) {
        return build(ErrorCode.OPTIMISTIC_LOCK, Map.of(), null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> integrity(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getMessage());
        return build(ErrorCode.DUPLICATE, Map.of(), null);
    }

    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ApiErrorResponse> denied(Exception ex) {
        return build(ErrorCode.FORBIDDEN, Map.of(), null);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> unauthenticated(AuthenticationException ex) {
        return build(ErrorCode.UNAUTHORIZED, Map.of(), null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> tooLarge(MaxUploadSizeExceededException ex) {
        return build(ErrorCode.FILE_TOO_LARGE, Map.of("maxMb", 5), null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> noResource(NoResourceFoundException ex) {
        return build(ErrorCode.NOT_FOUND, Map.of(), null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> method(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(ApiErrorResponse.of(ErrorCode.NOT_FOUND.name(), Map.of(), null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> unexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return build(ErrorCode.GENERIC, Map.of(), null);
    }

    private static ResponseEntity<ApiErrorResponse> build(ErrorCode code, Map<String, Object> params, List<FieldErrorDto> fields) {
        return ResponseEntity.status(code.status()).body(ApiErrorResponse.of(code.name(), params, fields));
    }

    /** Maps Bean Validation constraints to the frontend's {@code validation.*} message keys. */
    static FieldErrorDto toFieldError(FieldError fe) {
        String constraint = fe.getCode() == null ? "" : fe.getCode();
        Object[] args = fe.getArguments();
        Map<String, Object> params = new LinkedHashMap<>();
        String code = switch (constraint) {
            case "NotNull", "NotBlank", "NotEmpty" -> "required";
            case "Size" -> {
                // Arguments: [resolvable, max, min]
                if (args != null && args.length >= 3) {
                    params.put("min", args[2]);
                    params.put("max", args[1]);
                }
                yield fe.getRejectedValue() != null && String.valueOf(fe.getRejectedValue()).length() < asInt(params.get("min")) ? "minLength" : "maxLength";
            }
            case "Min", "DecimalMin" -> {
                if (args != null && args.length >= 2) params.put("min", String.valueOf(args[args.length - 1]));
                yield "min";
            }
            case "Max", "DecimalMax" -> {
                if (args != null && args.length >= 2) params.put("max", String.valueOf(args[args.length - 1]));
                yield "max";
            }
            case "Positive" -> "positive";
            case "Email" -> "email";
            case "Digits" -> "number";
            default -> "invalid";
        };
        return new FieldErrorDto(fe.getField(), code, params);
    }

    private static int asInt(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }
}
