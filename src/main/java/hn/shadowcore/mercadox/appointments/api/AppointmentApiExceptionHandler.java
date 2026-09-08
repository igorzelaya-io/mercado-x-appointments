package hn.shadowcore.mercadox.appointments.api;

import hn.shadowcore.mercadox.appointments.google.oauth.GoogleCalendarAuthorizationException;
import hn.shadowcore.mercadox.appointments.security.CorrelationIdFilter;
import hn.shadowcore.mercadox.library.entity.response.ApiError;
import hn.shadowcore.mercadox.library.entity.response.ApiErrorCode;
import hn.shadowcore.mercadox.library.entity.response.FieldViolation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@RestControllerAdvice
public class AppointmentApiExceptionHandler {

    private final Clock clock;

    public AppointmentApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(GoogleCalendarAuthorizationException.class)
    public ResponseEntity<ApiError> handleGoogleAuthorization(
            GoogleCalendarAuthorizationException exception,
            HttpServletRequest request) {
        return switch (exception.getReason()) {
            case INVALID_RETURN_PATH -> error(
                    HttpStatus.BAD_REQUEST,
                    ApiErrorCode.INVALID_REQUEST,
                    exception.getMessage(),
                    request,
                    List.of());
            case CONNECTION_ALREADY_ACTIVE -> error(
                    HttpStatus.CONFLICT,
                    ApiErrorCode.GOOGLE_CONNECTION_ALREADY_ACTIVE,
                    exception.getMessage(),
                    request,
                    List.of());
            case STATE_STORE_UNAVAILABLE -> error(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ApiErrorCode.TEMPORARY_UNAVAILABLE,
                    exception.getMessage(),
                    request,
                    List.of());
        };
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldViolation> violations = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return error(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_REQUEST,
                "The request is invalid.",
                request,
                violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ApiErrorCode.INVALID_REQUEST,
                "The request body is invalid.",
                request,
                List.of());
    }

    private ResponseEntity<ApiError> error(HttpStatus status,
                                           ApiErrorCode code,
                                           String message,
                                           HttpServletRequest request,
                                           List<FieldViolation> violations) {
        String correlationId = correlationId(request);
        ApiError body = new ApiError(
                status,
                code,
                message,
                Instant.now(clock),
                correlationId,
                violations);
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(CorrelationIdFilter.HEADER_NAME, correlationId)
                .body(body);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value instanceof String string ? string : "unavailable";
    }
}
