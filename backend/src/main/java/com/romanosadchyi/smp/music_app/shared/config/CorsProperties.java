package com.romanosadchyi.smp.music_app.shared.config;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * @param allowedOrigins the origins the frontend is served from ({@code CORS_ALLOWED_ORIGINS}, comma-separated);
 *                       they are allowed for REST (CORS) and for the WebSocket handshake of {@code /ws} and
 *                       {@code /ws-raw}
 */
@Validated
@ConfigurationProperties("app.cors")
public record CorsProperties(@NotEmpty List<String> allowedOrigins) {
}
