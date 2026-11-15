package com.romanosadchyi.smp.music_app.rooms.internal.mapping;

import com.romanosadchyi.smp.music_app.shared.exception.ConflictException;
import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.exception.NotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.stereotype.Component;

/**
 * Turns a refused chat message into the error its sender gets on {@code /user/queue/rooms/{roomId}/chat-errors}, with
 * the same body and codes as the REST API's {@code GlobalExceptionHandler} (KAN-51).
 */
@Slf4j
@Component
public class RoomChatErrorMappingService {

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
            return response(HttpStatus.BAD_REQUEST, "The message could not be read");
        }
        if (error instanceof ConflictException) {
            return response(HttpStatus.CONFLICT, error.getMessage());
        }
        log.error("Unhandled error in the room chat", error);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ErrorResponseDto response(HttpStatus status, String message) {
        return new ErrorResponseDto(status.toString(), message);
    }
}
