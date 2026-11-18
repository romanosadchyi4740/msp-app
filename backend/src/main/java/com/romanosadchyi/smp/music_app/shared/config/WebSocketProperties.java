package com.romanosadchyi.smp.music_app.shared.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param rawEndpointEnabled whether the plain WebSocket endpoint {@code /ws-raw} (no SockJS) is registered, for
 *                           Postman, the integration tests and the test data scripts; off unless a profile turns
 *                           it on, so production serves only {@code /ws}
 */
@ConfigurationProperties("app.websocket")
public record WebSocketProperties(boolean rawEndpointEnabled) {
}
