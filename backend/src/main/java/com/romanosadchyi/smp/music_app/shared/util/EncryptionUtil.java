package com.romanosadchyi.smp.music_app.shared.util;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts stored text, such as chat messages, with AES-256-GCM (KAN-21, NFR-5). Every value gets a random 12-byte
 * IV, so the same text never gives the same ciphertext, and the GCM tag makes any change to a stored value fail to
 * decrypt. A value is stored as {@code v2:} followed by base64(IV, ciphertext and tag).
 *
 * <p>Only this format is read: a value that is not one, or that does not decrypt, throws an
 * {@link EncryptionException} instead of being passed through as plain text. The key is
 * {@link EncryptionProperties#secretKey()}, which must be 32 bytes as UTF-8; the application does not start with
 * another length.</p>
 */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EncryptionUtil {

    static final String PREFIX = "v2:";
    static final int KEY_BYTES = 32;
    static final int IV_BYTES = 12;
    static final int TAG_BITS = 128;
    static final String ALGORITHM = "AES/GCM/NoPadding";

    SecretKey key;
    SecureRandom random = new SecureRandom();

    public EncryptionUtil(EncryptionProperties properties) {
        byte[] keyBytes = properties.secretKey().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException("encryption.secret-key must be " + KEY_BYTES + " bytes as UTF-8, not "
                    + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /**
     * The stored form of the text; null stays null.
     */
    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        byte[] iv = new byte[IV_BYTES];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] sealed = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] stored = ByteBuffer.allocate(IV_BYTES + sealed.length).put(iv).put(sealed).array();
            return PREFIX + Base64.getEncoder().encodeToString(stored);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("Could not encrypt a value", e);
        }
    }

    /**
     * The text of a stored value; null stays null.
     *
     * @throws EncryptionException if the value is not in the {@code v2:} format, was changed, or was encrypted with
     *                             another key
     */
    public String decrypt(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            throw new EncryptionException("A stored value is not in the v2 format");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw new EncryptionException("A stored value is not valid base64", e);
        }
        if (bytes.length < IV_BYTES + TAG_BITS / 8) {
            throw new EncryptionException("A stored value is too short");
        }
        try {
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES));
            byte[] plain = cipher.doFinal(bytes, IV_BYTES, bytes.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new EncryptionException("A stored value could not be decrypted", e);
        }
    }
}
