package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.JwtService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Authenticates a STOMP session on {@code CONNECT} from the {@code Authorization: Bearer <jwt>} native header.
 * The session user is an {@link AuthenticatedUser}, whose name is the user id, so user destinations
 * ({@code /user/queue/...}) resolve by user id.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    JwtService jwtService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    AuthenticatedUser user = jwtService.parseToken(authHeader.substring(7));
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(user, null, user.authorities());
                    accessor.setUser(authToken);
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                    log.info("WebSocket authenticated for user: {}", user.id());
                } catch (Exception e) {
                    log.error("WebSocket auth error: {}", e.getMessage());
                }
            } else {
                log.warn("No valid Authorization header found in WebSocket CONNECT frame");
            }
        }
        return message;
    }

}
