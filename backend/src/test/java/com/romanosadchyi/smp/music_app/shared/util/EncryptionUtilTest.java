package com.romanosadchyi.smp.music_app.shared.util;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionUtilTest {

    static final String KEY = "12345678901234567890123456789012";

    final EncryptionUtil encryptionUtil = new EncryptionUtil(new EncryptionProperties(KEY));

    @Test
    void encryptAndDecrypt_ReturnsTheText() {
        String text = "Top secret message, with ünïcödé and emoji 🎸";

        String stored = encryptionUtil.encrypt(text);

        assertThat(stored).startsWith("v2:").doesNotContain("Top secret");
        assertThat(encryptionUtil.decrypt(stored)).isEqualTo(text);
    }

    @Test
    void encrypt_SameTextTwice_GivesDifferentValues() {
        String first = encryptionUtil.encrypt("Same text");
        String second = encryptionUtil.encrypt("Same text");

        assertThat(first).isNotEqualTo(second);
        assertThat(encryptionUtil.decrypt(first)).isEqualTo(encryptionUtil.decrypt(second));
    }

    @Test
    void encrypt_StoresIvCiphertextAndTag() {
        String stored = encryptionUtil.encrypt("abc");

        byte[] bytes = Base64.getDecoder().decode(stored.substring(EncryptionUtil.PREFIX.length()));

        // 12-byte IV, 3 bytes of ciphertext, 16-byte tag
        assertThat(bytes).hasSize(12 + 3 + 16);
    }

    @Test
    void encryptAndDecrypt_EmptyText_ReturnsEmpty() {
        assertThat(encryptionUtil.decrypt(encryptionUtil.encrypt(""))).isEmpty();
    }

    @Test
    void encryptAndDecrypt_Null_ReturnsNull() {
        assertThat(encryptionUtil.encrypt(null)).isNull();
        assertThat(encryptionUtil.decrypt(null)).isNull();
    }

    @Test
    void decrypt_ChangedValue_Throws() {
        byte[] bytes = Base64.getDecoder().decode(encryptionUtil.encrypt("Pay 10 EUR").substring(EncryptionUtil.PREFIX.length()));
        bytes[bytes.length - 20] ^= 1;
        String tampered = "v2:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> encryptionUtil.decrypt(tampered))
                .isInstanceOf(EncryptionException.class)
                .hasMessage("A stored value could not be decrypted");
    }

    @Test
    void decrypt_OtherKey_Throws() {
        EncryptionUtil other = new EncryptionUtil(new EncryptionProperties("abcdefghijabcdefghijabcdefghij12"));
        String stored = other.encrypt("Hello");

        assertThatThrownBy(() -> encryptionUtil.decrypt(stored)).isInstanceOf(EncryptionException.class);
    }

    @Test
    void decrypt_OldOrPlainValue_ThrowsInsteadOfPassingItThrough() {
        // What the AES-CBC format stored, and plain text: neither is given back as if it were the message
        assertThatThrownBy(() -> encryptionUtil.decrypt("q0n8m9hO1o3kU6RL2ZK0Ag=="))
                .isInstanceOf(EncryptionException.class)
                .hasMessage("A stored value is not in the v2 format");
        assertThatThrownBy(() -> encryptionUtil.decrypt("Hello"))
                .isInstanceOf(EncryptionException.class);
    }

    @Test
    void decrypt_NotBase64_Throws() {
        assertThatThrownBy(() -> encryptionUtil.decrypt("v2:not base64!"))
                .isInstanceOf(EncryptionException.class)
                .hasMessage("A stored value is not valid base64");
    }

    @Test
    void decrypt_TooShort_Throws() {
        String shortValue = "v2:" + Base64.getEncoder().encodeToString(new byte[27]);

        assertThatThrownBy(() -> encryptionUtil.decrypt(shortValue))
                .isInstanceOf(EncryptionException.class)
                .hasMessage("A stored value is too short");
    }

    @Test
    void decrypt_WrongValue_MessageOmitsTheValue() {
        assertThatThrownBy(() -> encryptionUtil.decrypt("v2:" + Base64.getEncoder()
                .encodeToString("secret plain text that is long enough".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(EncryptionException.class)
                .message().doesNotContain("secret");
    }

    @Test
    void constructor_KeyNot32Bytes_Throws() {
        assertThatThrownBy(() -> new EncryptionUtil(new EncryptionProperties("short")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 bytes");
        // 32 characters, but 33 bytes as UTF-8
        assertThatThrownBy(() -> new EncryptionUtil(new EncryptionProperties("é" + "x".repeat(31))))
                .isInstanceOf(IllegalStateException.class);
    }
}
