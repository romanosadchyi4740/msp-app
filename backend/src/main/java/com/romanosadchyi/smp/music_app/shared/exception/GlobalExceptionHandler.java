package com.romanosadchyi.smp.music_app.shared.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    static final String UNEXPECTED_ERROR = "An unexpected error occurred. Node-link compromised.";

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleNotFoundException(NotFoundException e) {
        return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponseDto> handleConflictException(ConflictException e) {
        return createErrorResponse(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(UserNotAuthorizedException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotAuthorizedException(UserNotAuthorizedException e) {
        return createErrorResponse(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException e) {
        return createErrorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Exceptions that carry their own status ({@link ResponseStatusException}, and Spring MVC's errors such as an
     * unknown path or a missing parameter) answer with that status (KAN-100); a client error is logged at WARN by
     * status and type only, without a stack trace. Everything else is a 500. Database exceptions are logged without
     * their message, which can quote row values such as an email.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneralException(Exception e) {
        if (e instanceof ErrorResponse errorResponse) {
            HttpStatus status = HttpStatus.resolve(errorResponse.getStatusCode().value());
            if (status != null && status.is4xxClientError()) {
                log.warn("Request refused with {} ({})", status.value(), e.getClass().getSimpleName());
                return createErrorResponse(status, clientMessage(e, errorResponse));
            }
        }
        if (e instanceof DataAccessException) {
            log.error("Unhandled {} caused by {}; message withheld, it may hold row values", e.getClass().getName(),
                    NestedExceptionUtils.getMostSpecificCause(e).getClass().getName());
        } else {
            log.error("Unhandled exception occurred: ", e);
        }
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR);
    }

    private static String clientMessage(Exception e, ErrorResponse errorResponse) {
        if (e instanceof ResponseStatusException statusException && statusException.getReason() != null) {
            return statusException.getReason();
        }
        return errorResponse.getBody().getDetail();
    }

    private ResponseEntity<ErrorResponseDto> createErrorResponse(HttpStatus status, String message) {
        return new ResponseEntity<>(
                new ErrorResponseDto(status.toString(), message),
                status
        );
    }
}
