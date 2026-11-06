package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
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
import java.util.regex.Pattern;

/**
 * Follows the subscriptions to rooms' topics (KAN-36). A participant is online in a room while at least one of their
 * sessions is subscribed to {@code /topic/rooms/{roomId}/participants}, so two open tabs count once and closing one
 * keeps the user online.
 * <p>
 * The STOMP guard checks membership only when a subscription is made, so a participant who leaves keeps what they
 * subscribed to. {@link #revoke} ends those subscriptions at the broker, and the room's messages are no longer
 * delivered to the leaver's sessions ({@link StompDeliveryFilter}): a subscription that was on its way when the
 * participant left may reach the broker after the unsubscribe, and a subscription event that arrives after the leave
 * is checked against membership again.
 * <p>
 * The state lives in memory, which suits the single instance the in-memory broker allows. Presence changes of one
 * user in one room are serialized, so a quick disconnect and reconnect cannot store offline after online; other users
 * and rooms are not held up.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomSubscriptionTracker implements StompDeliveryFilter {

    static final String ROOM_TOPICS = "/topic/rooms/";
    static final Pattern PARTICIPANTS_TOPIC = Pattern.compile("^/topic/rooms/[^/]+/participants$");

    RoomPresenceService roomPresenceService;
    RoomParticipantRepository participantRepository;
    SimpMessagingTemplate messagingTemplate;
    Map<SubscriptionKey, RoomSubscription> subscriptions = new ConcurrentHashMap<>();
    Map<RoomUser, Object> presenceLocks = new ConcurrentHashMap<>();
    /**
     * Sessions that may no longer receive a room's messages, until they disconnect or subscribe there as a
     * participant again.
     */
    Set<RoomSession> revoked = ConcurrentHashMap.newKeySet();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        Optional<UUID> userId = userIdOf(event.getUser());
        if (destination == null || !destination.startsWith(ROOM_TOPICS) || userId.isEmpty()) {
            return;
        }
        Optional<UUID> roomId = RoomStompGuard.roomIdOf(destination);
        if (roomId.isEmpty()) {
            return;
        }
        SubscriptionKey key = new SubscriptionKey(accessor.getSessionId(), accessor.getSubscriptionId());
        RoomUser roomUser = new RoomUser(roomId.get(), userId.get());
        RoomSession roomSession = new RoomSession(key.sessionId(), roomId.get());
        synchronized (lockOf(roomUser)) {
            if (participantRepository.findByRoomIdAndUserId(roomUser.roomId(), roomUser.userId()).isEmpty()) {
                // Left the room after the guard let this subscription through
                revoked.add(roomSession);
                unsubscribe(key);
                return;
            }
            revoked.remove(roomSession);
            boolean wasOnline = isOnline(roomUser);
            boolean presence = PARTICIPANTS_TOPIC.matcher(destination).matches();
            subscriptions.put(key, new RoomSubscription(roomUser, presence));
            if (presence && !wasOnline) {
                markOnline(roomUser);
            }
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
        SubscriptionKey key = new SubscriptionKey(accessor.getSessionId(), accessor.getSubscriptionId());
        RoomSubscription subscription = subscriptions.get(key);
        if (subscription == null) {
            return;
        }
        synchronized (lockOf(subscription.roomUser())) {
            if (subscriptions.remove(key) != null) {
                markOfflineIfGone(subscription);
            }
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        revoked.removeIf(roomSession -> roomSession.sessionId().equals(sessionId));
        List<Map.Entry<SubscriptionKey, RoomSubscription>> ofSession = subscriptions.entrySet().stream()
                .filter(entry -> entry.getKey().sessionId().equals(sessionId))
                .toList();
        for (Map.Entry<SubscriptionKey, RoomSubscription> entry : ofSession) {
            synchronized (lockOf(entry.getValue().roomUser())) {
                if (subscriptions.remove(entry.getKey()) != null) {
                    markOfflineIfGone(entry.getValue());
                }
            }
        }
    }

    /**
     * Ends the user's subscriptions to the room's topics in every session, after they left the room, and stops the
     * room's messages to those sessions. Their presence is not stored again: the participant row is gone.
     */
    public void revoke(UUID roomId, UUID userId) {
        RoomUser roomUser = new RoomUser(roomId, userId);
        synchronized (lockOf(roomUser)) {
            List<SubscriptionKey> keys = subscriptions.entrySet().stream()
                    .filter(entry -> entry.getValue().roomUser().equals(roomUser))
                    .map(Map.Entry::getKey)
                    .toList();
            for (SubscriptionKey key : keys) {
                subscriptions.remove(key);
                revoked.add(new RoomSession(key.sessionId(), roomId));
                unsubscribe(key);
            }
        }
    }

    @Override
    public boolean mayDeliver(String sessionId, String destination) {
        if (!destination.startsWith(ROOM_TOPICS) || revoked.isEmpty()) {
            return true;
        }
        return RoomStompGuard.roomIdOf(destination)
                .map(roomId -> !revoked.contains(new RoomSession(sessionId, roomId)))
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

    private Object lockOf(RoomUser roomUser) {
        return presenceLocks.computeIfAbsent(roomUser, ignored -> new Object());
    }

    private boolean isOnline(RoomUser roomUser) {
        return subscriptions.values().stream()
                .anyMatch(subscription -> subscription.presence() && subscription.roomUser().equals(roomUser));
    }

    private void markOfflineIfGone(RoomSubscription removed) {
        if (removed.presence() && !isOnline(removed.roomUser())) {
            try {
                roomPresenceService.markOffline(removed.roomUser().roomId(), removed.roomUser().userId());
            } catch (RuntimeException e) {
                log.error("Could not mark user {} offline in room {}", removed.roomUser().userId(),
                        removed.roomUser().roomId(), e);
            }
        }
    }

    private void markOnline(RoomUser roomUser) {
        try {
            roomPresenceService.markOnline(roomUser.roomId(), roomUser.userId());
        } catch (RuntimeException e) {
            log.error("Could not mark user {} online in room {}", roomUser.userId(), roomUser.roomId(), e);
        }
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

    record RoomUser(UUID roomId, UUID userId) {
    }

    record RoomSession(String sessionId, UUID roomId) {
    }

    /**
     * @param presence whether it is the participants topic, which counts for presence
     */
    record RoomSubscription(RoomUser roomUser, boolean presence) {
    }
}
