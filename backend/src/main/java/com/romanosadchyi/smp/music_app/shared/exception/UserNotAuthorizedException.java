package com.romanosadchyi.smp.music_app.shared.exception;

import lombok.experimental.StandardException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
@StandardException
public class UserNotAuthorizedException extends RuntimeException {
}
