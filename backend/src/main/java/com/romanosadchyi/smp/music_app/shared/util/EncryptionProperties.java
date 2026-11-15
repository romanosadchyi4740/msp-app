package com.romanosadchyi.smp.music_app.shared.util;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Validates the key at startup; {@link EncryptionUtil} also refuses a key of another length than 32 bytes.
 *
 * @param secretKey the AES-256 key, 32 bytes as UTF-8 ({@code ENCRYPTION_SECRET})
 */
@Validated
@ConfigurationProperties("encryption")
public record EncryptionProperties(@NotBlank String secretKey) {
}
