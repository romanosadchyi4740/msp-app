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
 * ({@code /user/queue/...}) resolve by user id. A {@code CONNECT} without a valid token, or with the token of a
 * deleted account, is refused: the client gets an ERROR frame and the session is closed, so no anonymous session
 * exists (KAN-23). {@code SEND} and {@code SUBSCRIBE} frames of a session without a user, or whose account was
 * deleted after it connected, are rejected.
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
        if (accessor == null) {
            return message;
        }
        if (isFromOpenSession(accessor.getCommand())) {
            requireActive(accessor.getUser());
        }
        // STOMP 1.2 clients may open with a STOMP frame instead of CONNECT; both start a session
        if (StompCommand.CONNECT.equals(accessor.getCommand()) || StompCommand.STOMP.equals(accessor.getCommand())) {
            AuthenticatedUser user = authenticate(accessor.getFirstNativeHeader("Authorization"));
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
            log.info("WebSocket authenticated for user: {}", user.id());
        }
        return message;
    }

    /**
     * @throws AccessDeniedException if the header holds no valid token, the account is not active, or its status
     *                               cannot be checked
     */
    private AuthenticatedUser authenticate(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Refused WebSocket CONNECT without a bearer token");
            throw new AccessDeniedException("Authentication required");
        }
        AuthenticatedUser user;
        try {
            user = jwtService.parseToken(authHeader.substring(7));
        } catch (RuntimeException e) {
            log.warn("Refused WebSocket CONNECT with an invalid token: {}", e.getMessage());
            throw new AccessDeniedException("Invalid token");
        }
        boolean active;
        try {
            active = accountStatus.isActive(user.id());
        } catch (RuntimeException e) {
            log.error("Could not check the account status of user {} on WebSocket CONNECT", user.id(), e);
            throw new AccessDeniedException("Could not check the account");
        }
        if (!active) {
            log.warn("Refused WebSocket CONNECT of inactive account {}", user.id());
            throw new AccessDeniedException("Account is not active");
        }
        return user;
    }

    /**
     * Frames a client sends on an already connected session, which carries the user set at CONNECT.
     */
    private static boolean isFromOpenSession(StompCommand command) {
        return StompCommand.SEND.equals(command) || StompCommand.SUBSCRIBE.equals(command);
    }

    /**
     * Rejects frames from a session without a user, and from one whose account was deleted after it connected.
     */
    private void requireActive(Principal sessionUser) {
        if (!(sessionUser instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AccessDeniedException("Authentication required");
        }
        if (!accountStatus.isActive(user.id())) {
            throw new AccessDeniedException("Account is not active");
        }
    }
}
