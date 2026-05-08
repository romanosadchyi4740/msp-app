package com.romanosadchyi.smp.music_app.exception;

import com.romanosadchyi.smp.music_app.model.dto.response.ErrorResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({
            UserNotFoundException.class,
            TrackNotFoundException.class,
            PlaylistNotFoundException.class,
            AlbumNotFoundException.class,
            ChatNotFoundException.class
    })
    public ResponseEntity<ErrorResponseDto> handleNotFoundException(RuntimeException e) {
        return createErrorResponse(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(MusicTrackFileAlreadyExistsException.class)
    public ResponseEntity<ErrorResponseDto> handleMusicTrackFileAlreadyExistsException(
            MusicTrackFileAlreadyExistsException e
    ) {
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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGeneralException(Exception e) {
        log.error("Unhandled exception occurred: ", e);
        return createErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred. Node-link compromised.");
    }

    private ResponseEntity<ErrorResponseDto> createErrorResponse(HttpStatus status, String message) {
        return new ResponseEntity<>(
                new ErrorResponseDto(status.toString(), message),
                status
        );
    }

}
