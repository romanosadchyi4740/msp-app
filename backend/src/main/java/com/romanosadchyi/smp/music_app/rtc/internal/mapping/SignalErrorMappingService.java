package com.romanosadchyi.smp.music_app.rtc.internal.mapping;

import com.romanosadchyi.smp.music_app.shared.exception.ErrorResponseDto;
import com.romanosadchyi.smp.music_app.shared.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.invocation.MethodArgumentResolutionException;
import org.springframework.stereotype.Component;

/**
 * Turns a failed signal into the error its sender gets on {@code /user/queue/errors}, with the same body and codes as
 * the REST API's {@code GlobalExceptionHandler}.
 */
@Slf4j
@Component
public class SignalErrorMappingService {

    public ErrorResponseDto map(Throwable error) {
        if (error instanceof NotFoundException) {
            return response(HttpStatus.NOT_FOUND, error.getMessage());
        }
        if (error instanceof IllegalArgumentException) {
            return response(HttpStatus.BAD_REQUEST, error.getMessage());
        }
        if (error instanceof MessageConversionException || error instanceof MethodArgumentResolutionException) {
            // Clients can cause these at will (a malformed payload or room id), so they are not logged as errors
            return response(HttpStatus.BAD_REQUEST, "The signal could not be read");
        }
        // The type only: the message of a failed send could quote the payload
        log.error("Unhandled error in the signaling channel: {}", error.getClass().getName());
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    }

    private static ErrorResponseDto response(HttpStatus status, String message) {
        return new ErrorResponseDto(status.toString(), message);
    }
}
