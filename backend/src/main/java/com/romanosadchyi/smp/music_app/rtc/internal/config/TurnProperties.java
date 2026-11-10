package com.romanosadchyi.smp.music_app.rtc.internal.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * The ICE servers handed to browsers.
 *
 * @param secret               the coturn {@code static-auth-secret} the TURN credentials are signed with
 *                             ({@code TURN_SECRET}); never sent to clients
 * @param urls                 the TURN addresses, e.g. {@code turn:host:3478?transport=udp},
 *                             {@code turn:host:3478?transport=tcp} and {@code turns:host:443?transport=tcp}
 * @param stunUrls             the STUN addresses; may be empty
 * @param credentialTtlSeconds how long a credential works, 1 to 6 hours
 */
@Validated
@ConfigurationProperties("turn")
public record TurnProperties(
        @NotBlank String secret,
        @NotEmpty List<@NotBlank String> urls,
        List<@NotBlank String> stunUrls,
        @Min(3600) @Max(21600) long credentialTtlSeconds
) {

    public TurnProperties {
        stunUrls = stunUrls == null ? List.of() : List.copyOf(stunUrls);
    }
}
