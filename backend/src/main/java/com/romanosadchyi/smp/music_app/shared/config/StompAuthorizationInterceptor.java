package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.StompDestinationGuard;
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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Checks every {@code SUBSCRIBE} and {@code SEND} against the {@link StompDestinationGuard}s of the modules (KAN-23):
 * a destination a guard claims is open only to the users it lets in, e.g. a room's topics only to its participants.
 * Clients send only to {@code /app} handlers: a {@code SEND} straight to a broker destination ({@code /topic},
 * {@code /queue}, {@code /user}) would let a participant forge the room state or write into another user's queue.
 * Destinations with an empty segment ({@code //}) or a trailing slash are refused, since the handler mapping would
 * still route them while the guards do not recognise them.
 * Pattern subscriptions are refused outright, since the simple broker would match them against every destination,
 * so {@code /topic/rooms/**} would hear every room. Runs after {@link JwtStompChannelInterceptor}, which has set the
 * session user. A refused frame makes the client get an ERROR frame and the session close.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StompAuthorizationInterceptor implements ChannelInterceptor {

    static final String APP_PREFIX = "/app/";

    /**
     * Non-empty segments only: the handler mapping drops empty segments, so {@code /app//broadcast/...} would reach
     * the broadcast handler while no guard recognises it.
     */
    static final Pattern WELL_FORMED = Pattern.compile("^(/[^/]+)+$");

    List<StompDestinationGuard> guards;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        StompCommand command = accessor.getCommand();
        if (!StompCommand.SUBSCRIBE.equals(command) && !StompCommand.SEND.equals(command)) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new AccessDeniedException("A destination is required");
        }
        if (!WELL_FORMED.matcher(destination).matches()) {
            log.warn("Refused {} to a malformed destination", command);
            throw new AccessDeniedException("Malformed destination");
        }
        if (StompCommand.SEND.equals(command) && !destination.startsWith(APP_PREFIX)) {
            log.warn("Refused a SEND to the broker destination {}", destination);
            throw new AccessDeniedException("Clients send only to /app destinations");
        }
        if (StompCommand.SUBSCRIBE.equals(command) && isPattern(destination)) {
            log.warn("Refused a pattern subscription to {}", destination);
            throw new AccessDeniedException("Pattern subscriptions are not allowed");
        }
        for (StompDestinationGuard guard : guards) {
            if (guard.guards(destination) && !guard.mayAccess(userOf(accessor).id(), destination)) {
                log.warn("Refused {} to {} for user {}", command, destination, userOf(accessor).id());
                throw new AccessDeniedException("Access denied");
            }
        }
        return message;
    }

    private static boolean isPattern(String destination) {
        return destination.indexOf('*') >= 0 || destination.indexOf('?') >= 0 || destination.indexOf('{') >= 0;
    }

    private static AuthenticatedUser userOf(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        throw new AccessDeniedException("Authentication required");
    }
}
