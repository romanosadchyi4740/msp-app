package com.romanosadchyi.smp.music_app.shared.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    static final CorsProperties CORS = new CorsProperties(List.of("https://app.example", "http://localhost:5173"));

    @Mock
    StompEndpointRegistry registry;

    @Mock
    StompWebSocketEndpointRegistration sockJsEndpoint;

    @Mock
    StompWebSocketEndpointRegistration rawEndpoint;

    @BeforeEach
    void setUp() {
        when(registry.addEndpoint("/ws")).thenReturn(sockJsEndpoint);
        when(sockJsEndpoint.setAllowedOrigins(any(String[].class))).thenReturn(sockJsEndpoint);
    }

    @Test
    void registerStompEndpoints_AllowsOnlyTheConfiguredOrigins() {
        when(registry.addEndpoint("/ws-raw")).thenReturn(rawEndpoint);

        config(true).registerStompEndpoints(registry);

        verify(sockJsEndpoint).setAllowedOrigins("https://app.example", "http://localhost:5173");
        verify(sockJsEndpoint).withSockJS();
        verify(rawEndpoint).setAllowedOrigins("https://app.example", "http://localhost:5173");
        verify(sockJsEndpoint, never()).setAllowedOriginPatterns(any(String[].class));
        verify(rawEndpoint, never()).setAllowedOriginPatterns(any(String[].class));
    }

    @Test
    void registerStompEndpoints_RawEndpointDisabled_RegistersOnlySockJs() {
        config(false).registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
        verify(registry, never()).addEndpoint("/ws-raw");
    }

    private WebSocketConfig config(boolean rawEndpointEnabled) {
        return new WebSocketConfig(null, null, null, null, CORS, new WebSocketProperties(rawEndpointEnabled));
    }
}
