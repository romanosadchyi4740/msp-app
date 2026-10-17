package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.AccountStatus;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates a STOMP session on {@code CONNECT} from the {@code Authorization: Bearer <jwt>} native header.
 * The session user is an {@link AuthenticatedUser}, whose name is the user id, so user destinations
 * ({@code /user/queue/...}) resolve by user id. A deleted account's still-valid token is refused on {@code CONNECT},
 * and {@code SEND} and {@code SUBSCRIBE} frames of a session whose account was deleted after it connected are
 * rejected.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class JwtStompChannelInterceptor implements ChannelInterceptor {

    JwtService jwtService;
    AccountStatus accountStatus;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && isFromOpenSession(accessor.getCommand())) {
            requireActive(accessor.getUser());
        }
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    AuthenticatedUser user = jwtService.parseToken(authHeader.substring(7));
                    if (!accountStatus.isActive(user.id())) {
                        log.warn("Refused WebSocket CONNECT of inactive account {}", user.id());
                        throw new AccessDeniedException("Account is not active");
                    }
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(user, null, user.authorities());
                    accessor.setUser(authToken);
                    log.info("WebSocket authenticated for user: {}", user.id());
                } catch (AccessDeniedException e) {
                    // Refuses the CONNECT: the client gets an ERROR frame and the session is closed
                    throw e;
                } catch (Exception e) {
                    log.error("WebSocket auth error: {}", e.getMessage());
                }
            } else {
                log.warn("No valid Authorization header found in WebSocket CONNECT frame");
            }
        }
        return message;
    }

    /**
     * Frames a client sends on an already connected session, which carries the user set at CONNECT.
     */
    private static boolean isFromOpenSession(StompCommand command) {
        return StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command);
    }

    /**
     * Rejects frames from a session whose account was deleted after it connected.
     */
    private void requireActive(Principal sessionUser) {
        if (sessionUser instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user
                && !accountStatus.isActive(user.id())) {
            throw new AccessDeniedException("Account is not active");
        }
    }
}
