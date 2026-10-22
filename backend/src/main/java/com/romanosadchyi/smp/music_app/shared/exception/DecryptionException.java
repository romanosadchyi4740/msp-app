package com.romanosadchyi.smp.music_app.shared.exception;

/**
 * A stored value is not valid ciphertext for the configured key. Never carries the value itself.
 */
public class DecryptionException extends RuntimeException {

    public DecryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
