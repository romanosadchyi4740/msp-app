package com.romanosadchyi.smp.music_app.exception;

import com.romanosadchyi.smp.music_app.model.dto.response.ErrorResponseDto;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserNotAuthorizedException.class)
    public ResponseEntity<ErrorResponseDto> handleUserNotAuthorizedException(UserNotAuthorizedException e) {
        return new ResponseEntity<>(
                new ErrorResponseDto(HttpStatus.FORBIDDEN.toString(), e.getMessage()),
                HttpStatus.FORBIDDEN
        );
    }

}
