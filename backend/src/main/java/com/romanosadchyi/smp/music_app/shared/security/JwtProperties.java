package com.romanosadchyi.smp.music_app.shared.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret     the HMAC-SHA256 signing key, Base64 of at least 32 bytes ({@code JWT_SECRET})
 * @param expiration token lifetime in milliseconds
 */
@Validated
@ConfigurationProperties("jwt")
public record JwtProperties(@NotBlank String secret, @Positive long expiration) {
}
