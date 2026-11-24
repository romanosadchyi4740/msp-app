package com.romanosadchyi.smp.music_app.shared.config;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    JwtStompChannelInterceptor jwtStompChannelInterceptor;
    StompAuthorizationInterceptor stompAuthorizationInterceptor;
    StompDeliveryInterceptor stompDeliveryInterceptor;
    WebSocketSessionRegistry webSocketSessionRegistry;
    CorsProperties corsProperties;
    WebSocketProperties webSocketProperties;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    /**
     * A handshake from a browser page on another origin is refused with 403 (KAN-22). Spring Security's CORS
     * filter, configured with the same origins, refuses it too; this keeps the endpoints safe on their own. Clients
     * that send no {@code Origin} header, such as Postman or the test STOMP client, are not browsers and are let
     * through; they still need a valid token on {@code CONNECT}.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] allowedOrigins = corsProperties.allowedOrigins().toArray(String[]::new);
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins)
                .withSockJS();
        if (webSocketProperties.rawEndpointEnabled()) {
            // Non-SockJS endpoint for Postman, the integration tests and the test data scripts
            registry.addEndpoint("/ws-raw")
                    .setAllowedOrigins(allowedOrigins);
        }
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.addDecoratorFactory(webSocketSessionRegistry);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Authentication first: the authorization checks need the session user it sets
        registration.interceptors(jwtStompChannelInterceptor, stompAuthorizationInterceptor);
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompDeliveryInterceptor);
    }
}
