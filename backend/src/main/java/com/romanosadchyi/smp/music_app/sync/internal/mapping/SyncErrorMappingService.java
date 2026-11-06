package com.romanosadchyi.smp.music_app.sync.internal.mapping;

import com.romanosadchyi.smp.music_app.shared.exception.ConflictException;
import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.exception.NotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

/**
 * Turns a failed sync command into the error its sender gets on {@code /user/queue/errors}, with the same body and
 * codes as the REST API's {@code GlobalExceptionHandler}.
 */
@Slf4j
@Component
public class SyncErrorMappingService {

    public ErrorResponseDto map(Throwable error) {
        if (error instanceof NotFoundException) {
            return response(HttpStatus.NOT_FOUND, error.getMessage());
        }
        if (error instanceof UserNotAuthorizedException) {
            return response(HttpStatus.FORBIDDEN, error.getMessage());
        }
        if (error instanceof IllegalArgumentException) {
            return response(HttpStatus.BAD_REQUEST, error.getMessage());
        }
        if (error instanceof MessageConversionException || error instanceof MethodArgumentResolutionException) {
            // Clients can cause these at will (a malformed payload or room id), so they are not logged as errors
            return response(HttpStatus.BAD_REQUEST, "The command could not be read");
        }
        if (error instanceof ConflictException) {
            return response(HttpStatus.CONFLICT, error.getMessage());
        }
        if (error instanceof ObjectOptimisticLockingFailureException) {
            return response(HttpStatus.CONFLICT, "The playback changed meanwhile; request a sync and try again");
        }
        log.error("Unhandled error in the sync channel", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ErrorResponseDto response(HttpStatus status, String message) {
        return new ErrorResponseDto(status.toString(), message);
    }
}
