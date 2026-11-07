package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.StompDeliveryFilter;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Follows the subscriptions to listening broadcasts ({@code /topic/broadcast/{chatId}/{broadcastId}}, KAN-93). The
 * STOMP guard ({@link BroadcastStompGuard}) checks who may read the chat only when a subscription is made, so a
 * member who rejects or declines a group chat keeps what they subscribed to. {@link #revoke} ends those
 * subscriptions at the broker, and the chat's broadcasts are no longer delivered to that member's sessions: a
 * subscription that was on its way may reach the broker after the unsubscribe, and one that arrives after the member
 * lost access is checked again. The same approach as the rooms' {@code RoomSubscriptionTracker}.
 * <p>
 * The state lives in memory, which suits the single instance the in-memory broker allows.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class BroadcastSubscriptionTracker implements StompDeliveryFilter {

    static final String BROADCAST_TOPICS = "/topic/broadcast/";

    BroadcastStompGuard broadcastStompGuard;
    SimpMessagingTemplate messagingTemplate;
    Map<SubscriptionKey, ChatUser> subscriptions = new ConcurrentHashMap<>();
    Map<ChatUser, Object> locks = new ConcurrentHashMap<>();
    /**
     * Sessions that may no longer receive a chat's broadcasts, until they disconnect or subscribe again while they
     * may read the chat.
     */
    Set<ChatSession> revoked = ConcurrentHashMap.newKeySet();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Optional<UUID> userId = userIdOf(event.getUser());
        if (destination == null || !destination.startsWith(BROADCAST_TOPICS) || userId.isEmpty()) {
            return;
        }
        Optional<UUID> chatId = BroadcastStompGuard.chatIdOf(destination);
        if (chatId.isEmpty()) {
            return;
        }
        SubscriptionKey key = new SubscriptionKey(accessor.getSessionId(), accessor.getSubscriptionId());
        ChatUser chatUser = new ChatUser(chatId.get(), userId.get());
        ChatSession chatSession = new ChatSession(key.sessionId(), chatId.get());
        synchronized (lockOf(chatUser)) {
            if (!broadcastStompGuard.mayAccess(chatUser.userId(), destination)) {
                // Lost access to the chat after the guard let this subscription through
                revoked.add(chatSession);
                unsubscribe(key);
                return;
            }
            revoked.remove(chatSession);
            subscriptions.put(key, chatUser);
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        subscriptions.remove(new SubscriptionKey(accessor.getSessionId(), accessor.getSubscriptionId()));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        revoked.removeIf(chatSession -> chatSession.sessionId().equals(sessionId));
        subscriptions.keySet().removeIf(key -> key.sessionId().equals(sessionId));
    }

    /**
     * Ends the user's subscriptions to the chat's broadcasts in every session, once they may no longer read it, and
     * stops the chat's broadcasts to those sessions.
     */
    public void revoke(UUID chatId, UUID userId) {
        ChatUser chatUser = new ChatUser(chatId, userId);
        synchronized (lockOf(chatUser)) {
            List<SubscriptionKey> keys = subscriptions.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(chatUser))
                    .map(Map.Entry::getKey)
                    .toList();
            for (SubscriptionKey key : keys) {
                subscriptions.remove(key);
                revoked.add(new ChatSession(key.sessionId(), chatId));
                unsubscribe(key);
            }
        }
    }

    @Override
    public boolean mayDeliver(String sessionId, String destination) {
        if (!destination.startsWith(BROADCAST_TOPICS) || revoked.isEmpty()) {
            return true;
        }
        return BroadcastStompGuard.chatIdOf(destination)
                .map(chatId -> !revoked.contains(new ChatSession(sessionId, chatId)))
                .orElse(true);
    }

    /**
     * Unregisters the subscription at the broker, as if the client had sent UNSUBSCRIBE.
     */
    private void unsubscribe(SubscriptionKey key) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
        accessor.setSessionId(key.sessionId());
        accessor.setSubscriptionId(key.subscriptionId());
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        try {
            messagingTemplate.getMessageChannel().send(message);
        } catch (RuntimeException e) {
            log.warn("Could not end subscription {} of session {}", key.subscriptionId(), key.sessionId(), e);
        }
    }

    private Object lockOf(ChatUser chatUser) {
        return locks.computeIfAbsent(chatUser, ignored -> new Object());
    }

    private static Optional<UUID> userIdOf(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user.id());
        }
        return Optional.empty();
    }

    record SubscriptionKey(String sessionId, String subscriptionId) {
    }

    record ChatUser(UUID chatId, UUID userId) {
    }

    record ChatSession(String sessionId, UUID chatId) {
    }
}
