package com.romanosadchyi.smp.music_app.shared.util;

/**
 * A value could not be encrypted or decrypted by {@link EncryptionUtil}. It never carries the value itself.
 */
public class EncryptionException extends RuntimeException {

    public EncryptionException(String message) {
        super(message);
    }

    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
