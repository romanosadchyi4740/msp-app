package com.romanosadchyi.smp.music_app.shared.exception;

import lombok.experimental.StandardException;

/**
 * Base type for errors caused by a conflict with the current state; {@link GlobalExceptionHandler} maps it to 409.
 */
@StandardException
public abstract class ConflictException extends RuntimeException {
}
