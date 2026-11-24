package com.romanosadchyi.smp.music_app.shared.exception;

import lombok.experimental.StandardException;

/**
 * Base type for "resource does not exist" errors; {@link GlobalExceptionHandler} maps it to 404.
 */
@StandardException
public abstract class NotFoundException extends RuntimeException {
}
