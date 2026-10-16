package com.romanosadchyi.smp.music_app.shared.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EncryptionUtilTest {

    private final String validSecretKey = "12345678901234567890123456789012"; // 32 bytes

    @BeforeEach
    void setUp() {
        // Initialize the static key via the instance method
        EncryptionUtil encryptionUtil = new EncryptionUtil();
        encryptionUtil.setSecretKey(validSecretKey);
    }

    @AfterEach
    void tearDown() {
        // Reset to prevent polluting other tests if we ever change it
        EncryptionUtil encryptionUtil = new EncryptionUtil();
        encryptionUtil.setSecretKey(validSecretKey);
    }

    @Test
    void encryptAndDecrypt_Success() {
        String originalText = "TopSecretMessage";

        String encryptedText = EncryptionUtil.encrypt(originalText);

        assertNotNull(encryptedText);
        assertNotEquals(originalText, encryptedText);

        String decryptedText = EncryptionUtil.decrypt(encryptedText);

        assertEquals(originalText, decryptedText);
    }

    @Test
    void encrypt_NullInput_ReturnsNull() {
        assertNull(EncryptionUtil.encrypt(null));
    }

    @Test
    void decrypt_NullInput_ReturnsNull() {
        assertNull(EncryptionUtil.decrypt(null));
    }

    @Test
    void decrypt_InvalidCipher_ReturnsOriginalText() {
        // If decryption fails, it's designed to return the input string
        String invalidCipher = "NotABase64EncodedAESString";
        
        String result = EncryptionUtil.decrypt(invalidCipher);
        
        assertEquals(invalidCipher, result);
    }

    @Test
    void encrypt_InvalidKey_ThrowsRuntimeException() {
        EncryptionUtil encryptionUtil = new EncryptionUtil();
        // Set an invalid key length (e.g., 5 bytes)
        encryptionUtil.setSecretKey("short");

        RuntimeException exception = assertThrows(RuntimeException.class, () -> {
            EncryptionUtil.encrypt("Test");
        });

        assertTrue(exception.getMessage().contains("Error while encrypting message"));
    }
}
