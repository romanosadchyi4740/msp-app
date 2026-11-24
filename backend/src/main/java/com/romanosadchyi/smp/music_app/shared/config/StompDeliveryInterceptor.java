package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.StompDeliveryFilter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * Drops broker messages on their way to a session when a module's {@link StompDeliveryFilter} says the session may
 * no longer receive them. Registered on the client outbound channel. The filters are looked up lazily: they send
 * through the messaging template, which the broker configuration that registers this interceptor creates.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class StompDeliveryInterceptor implements ChannelInterceptor {

    ObjectProvider<StompDeliveryFilter> filters;

    @Override
    public @Nullable Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(message);
        if (accessor.getMessageType() != SimpMessageType.MESSAGE) {
            return message;
        }
        String sessionId = accessor.getSessionId();
        String destination = accessor.getDestination();
        if (sessionId == null || destination == null) {
            return message;
        }
        boolean allowed = filters.orderedStream().allMatch(filter -> filter.mayDeliver(sessionId, destination));
        // Returning null stops the send, so the session never gets the message
        return allowed ? message : null;
    }
}
