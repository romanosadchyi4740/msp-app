package com.romanosadchyi.smp.music_app.shared.util;

import com.romanosadchyi.smp.music_app.shared.exception.DecryptionException;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionUtilTest {

    static final String KEY = "12345678901234567890123456789012";

    final EncryptionUtil encryptionUtil = new EncryptionUtil(new EncryptionProperties(KEY));

    @Test
    void encryptThenDecrypt_ReturnsTheOriginalText() {
        String encrypted = encryptionUtil.encrypt("Top secret, with ünïcödé");

        assertThat(encrypted).startsWith("v2:").doesNotContain("Top secret");
        assertThat(encryptionUtil.decrypt(encrypted)).isEqualTo("Top secret, with ünïcödé");
    }

    @Test
    void encrypt_SameTextTwice_GivesDifferentCiphertexts() {
        assertThat(encryptionUtil.encrypt("hello")).isNotEqualTo(encryptionUtil.encrypt("hello"));
    }

    @Test
    void encryptThenDecrypt_EmptyText_RoundTrips() {
        assertThat(encryptionUtil.decrypt(encryptionUtil.encrypt(""))).isEmpty();
    }

    @Test
    void nullInput_StaysNull() {
        assertThat(encryptionUtil.encrypt(null)).isNull();
        assertThat(encryptionUtil.decrypt(null)).isNull();
    }

    @Test
    void decrypt_LegacyCbcFormat_ReturnsTheOriginalText() throws Exception {
        assertThat(encryptionUtil.decrypt(legacyEncrypt("old message"))).isEqualTo("old message");
    }

    @Test
    void decrypt_Plaintext_Throws() {
        assertThatThrownBy(() -> encryptionUtil.decrypt("NotABase64EncodedAESString"))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    void decrypt_TamperedCiphertext_Throws() {
        String encrypted = encryptionUtil.encrypt("hello");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring(3));
        payload[payload.length - 1] ^= 1;

        assertThatThrownBy(() -> encryptionUtil.decrypt("v2:" + Base64.getEncoder().encodeToString(payload)))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    void decrypt_TooShortPayload_Throws() {
        assertThatThrownBy(() -> encryptionUtil.decrypt("v2:AAAA")).isInstanceOf(DecryptionException.class);
    }

    @Test
    void decrypt_EncryptedWithAnotherKey_Throws() {
        EncryptionUtil other = new EncryptionUtil(new EncryptionProperties("abcdefghijabcdefghijabcdefghij12"));

        assertThatThrownBy(() -> encryptionUtil.decrypt(other.encrypt("hello")))
                .isInstanceOf(DecryptionException.class);
    }

    @Test
    void constructor_KeyNot32Bytes_Throws() {
        assertThatThrownBy(() -> new EncryptionUtil(new EncryptionProperties("short")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }

    /**
     * The format written before KAN-21: AES-CBC with a fixed IV.
     */
    static String legacyEncrypt(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                new IvParameterSpec("encryptionIntVec".getBytes(StandardCharsets.UTF_8)));
        return Base64.getEncoder().encodeToString(cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8)));
    }
}
