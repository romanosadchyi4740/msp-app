package com.romanosadchyi.smp.music_app.shared.util;

import com.romanosadchyi.smp.music_app.shared.exception.DecryptionException;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Encrypts stored text with AES-256-GCM: {@code v2:} followed by Base64 of the random 12-byte IV, the ciphertext and
 * the 16-byte tag, so the same text never encrypts to the same value. Still reads the legacy format (AES-CBC with a
 * fixed IV, plain Base64), which {@code V21__EncryptMessagesWithAesGcm} migrates away from.
 */
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class EncryptionUtil {

    static final String VERSION_PREFIX = "v2:";
    static final int KEY_LENGTH = 32;
    static final int IV_LENGTH = 12;
    static final int TAG_LENGTH_BITS = 128;
    static final String LEGACY_IV = "encryptionIntVec";

    SecretKeySpec key;
    SecureRandom random = new SecureRandom();

    public EncryptionUtil(EncryptionProperties properties) {
        byte[] keyBytes = properties.secretKey().getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length != KEY_LENGTH) {
            throw new IllegalArgumentException(
                    "encryption.secret-key must be " + KEY_LENGTH + " bytes (AES-256), but is " + keyBytes.length);
        }
        key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plainText) {
        if (plainText == null) {
            return null;
        }
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();
            return VERSION_PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Error while encrypting", ex);
        }
    }

    /**
     * @throws DecryptionException if the value is not valid ciphertext for this key, in either format
     */
    public String decrypt(String cipherText) {
        if (cipherText == null) {
            return null;
        }
        try {
            return cipherText.startsWith(VERSION_PREFIX)
                    ? decryptGcm(Base64.getDecoder().decode(cipherText.substring(VERSION_PREFIX.length())))
                    : decryptLegacy(Base64.getDecoder().decode(cipherText));
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new DecryptionException("Stored value could not be decrypted", ex);
        }
    }

    private String decryptGcm(byte[] payload) throws GeneralSecurityException {
        if (payload.length < IV_LENGTH + TAG_LENGTH_BITS / 8) {
            throw new IllegalArgumentException("Ciphertext is too short");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, payload, 0, IV_LENGTH));
        return toText(cipher.doFinal(payload, IV_LENGTH, payload.length - IV_LENGTH));
    }

    private String decryptLegacy(byte[] encrypted) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(LEGACY_IV.getBytes(StandardCharsets.UTF_8)));
        return toText(cipher.doFinal(encrypted));
    }

    /**
     * Strict UTF-8, so bytes that only happen to have valid padding are rejected instead of turning into garbage.
     */
    private static String toText(byte[] bytes) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException ex) {
            throw new IllegalArgumentException("Decrypted bytes are not UTF-8", ex);
        }
    }
}
