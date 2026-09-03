package uk.gov.hmcts.cp.exceptions;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import uk.gov.hmcts.cp.domain.ErrorResponse;
import uk.gov.hmcts.cp.services.ClockService;

import java.util.stream.Collectors;

import static uk.gov.hmcts.cp.filters.TracingFilter.MDC_CORRELATION_ID;

@Slf4j
@RestControllerAdvice
@AllArgsConstructor
public class GlobalExceptionHandler {

    private final ClockService clockService;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(final MethodArgumentNotValidException exception) {
        final String message = exception.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .distinct()
            .collect(Collectors.joining(" "));
        log.warn("Request rejected by validation: {}", message);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse(message));
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ErrorResponse> handleMissingHeader(final MissingRequestHeaderException exception) {
        final String message = String.format("%s is required.", exception.getHeaderName());
        log.warn("Request rejected, missing header: {}", exception.getHeaderName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse(message));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadableBody(final HttpMessageNotReadableException exception) {
        log.warn("Request body could not be read: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse("The request could not be read."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResourceFound(final NoResourceFoundException exception) {
        log.warn("No handler for request: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(errorResponse("Not found."));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedMediaType(
            final HttpMediaTypeNotSupportedException exception) {
        log.warn("Unsupported media type: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
            .body(errorResponse("Send this request as application/json."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(
            final HttpRequestMethodNotSupportedException exception) {
        log.warn("Method not supported: {}", exception.getMessage());
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED).body(errorResponse("Method not allowed."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(final ResponseStatusException exception) {
        final String message = exception.getReason() == null
            ? "Request could not be completed."
            : exception.getReason();
        if (exception.getStatusCode().is5xxServerError()) {
            log.error("Request failed: {}", message);
        } else {
            log.warn("Request rejected: {}", message);
        }
        return ResponseEntity.status(exception.getStatusCode()).body(errorResponse(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknown(final Exception exception) {
        log.error("Unhandled exception", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(errorResponse("Something went wrong. Please try again."));
    }

    private ErrorResponse errorResponse(final String message) {
        return new ErrorResponse(message, clockService.now(), MDC.get(MDC_CORRELATION_ID));
    }
}
