package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.shared.exception.ConflictException;
import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.exception.NotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.stereotype.Service;

/**
 * Turns a refused broadcast command into the body the sender receives on {@code /user/queue/broadcast-errors}, with
 * the same status mapping as REST (KAN-109).
 */
@Slf4j
@Service
public class BroadcastErrorMappingService {

    public ErrorResponseDto map(Throwable error) {
        if (error instanceof NotFoundException) {
            return response(HttpStatus.NOT_FOUND, error.getMessage());
        }
        if (error instanceof UserNotAuthorizedException) {
            return response(HttpStatus.FORBIDDEN, error.getMessage());
        }
        if (error instanceof ConflictException) {
            return response(HttpStatus.CONFLICT, error.getMessage());
        }
        if (error instanceof IllegalArgumentException) {
            return response(HttpStatus.BAD_REQUEST, error.getMessage());
        }
        if (error instanceof MessageConversionException || error instanceof MethodArgumentResolutionException) {
            // Clients can cause these at will (a malformed payload or id), so they are not logged as errors
            return response(HttpStatus.BAD_REQUEST, "The message could not be read");
        }
        log.error("Unhandled error in a listening broadcast", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ErrorResponseDto response(HttpStatus status, String message) {
        return new ErrorResponseDto(status.toString(), message);
    }
}
