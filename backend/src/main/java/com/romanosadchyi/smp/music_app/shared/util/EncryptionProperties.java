package com.romanosadchyi.smp.music_app.shared.util;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validates the key at startup. {@code EncryptionUtil} still reads it through {@code @Value} until it becomes a
 * regular bean (KAN-21).
 *
 * @param secretKey the AES-256 key, 32 bytes as UTF-8 ({@code ENCRYPTION_SECRET})
 */
@Validated
@ConfigurationProperties("encryption")
public record EncryptionProperties(@NotBlank String secretKey) {
}
