package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastSubscriptionTrackerTest {

    @Mock
    BroadcastStompGuard broadcastStompGuard;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    MessageChannel brokerChannel;

    BroadcastSubscriptionTracker tracker;

    UUID chatId = UUID.randomUUID();
    UUID broadcastId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Principal user = new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(userId, "a@b.c", "ROLE_USER"), null, List.of());

    @BeforeEach
    void setUp() {
        tracker = new BroadcastSubscriptionTracker(broadcastStompGuard, messagingTemplate);
        lenient().when(messagingTemplate.getMessageChannel()).thenReturn(brokerChannel);
        lenient().when(broadcastStompGuard.mayAccess(eq(userId), anyString())).thenReturn(true);
    }

    @Test
    void revoke_EndsTheUsersSubscriptionsToTheChatsBroadcastsAndStopsDelivery() {
        UUID otherChat = UUID.randomUUID();
        tracker.onSubscribe(subscribe("s1", "sub-1", topic()));
        tracker.onSubscribe(subscribe("s2", "sub-2", topic()));
        tracker.onSubscribe(subscribe("s1", "sub-3", "/topic/broadcast/" + otherChat + "/" + broadcastId));

        tracker.revoke(chatId, userId);

        assertThat(unsubscribedIds()).containsExactlyInAnyOrder("sub-1", "sub-2");
        assertThat(tracker.mayDeliver("s1", topic())).isFalse();
        assertThat(tracker.mayDeliver("s2", topic())).isFalse();
        assertThat(tracker.mayDeliver("s1", "/topic/broadcast/" + otherChat + "/" + broadcastId)).isTrue();
        assertThat(tracker.mayDeliver("s3", topic())).isTrue();
    }

    @Test
    void revoke_OtherUsersSubscriptions_AreKept() {
        UUID otherUser = UUID.randomUUID();
        when(broadcastStompGuard.mayAccess(eq(otherUser), anyString())).thenReturn(true);
        tracker.onSubscribe(new SessionSubscribeEvent(this, subscribeMessage("s9", "sub-9", topic()),
                new UsernamePasswordAuthenticationToken(
                        new AuthenticatedUser(otherUser, "o@b.c", "ROLE_USER"), null, List.of())));

        tracker.revoke(chatId, userId);

        verifyNoInteractions(brokerChannel);
        assertThat(tracker.mayDeliver("s9", topic())).isTrue();
    }

    @Test
    void onSubscribe_AfterLosingAccess_UnsubscribesAndStopsDelivery() {
        when(broadcastStompGuard.mayAccess(userId, topic())).thenReturn(false);

        tracker.onSubscribe(subscribe("s1", "sub-1", topic()));

        assertThat(unsubscribedIds()).containsExactly("sub-1");
        assertThat(tracker.mayDeliver("s1", topic())).isFalse();
    }

    @Test
    void mayDeliver_AfterSubscribingAgainOrDisconnecting_IsAllowedAgain() {
        tracker.onSubscribe(subscribe("s1", "sub-1", topic()));
        tracker.revoke(chatId, userId);

        // Accepted the chat again, and subscribed through the guard
        tracker.onSubscribe(subscribe("s1", "sub-2", topic()));
        assertThat(tracker.mayDeliver("s1", topic())).isTrue();

        tracker.revoke(chatId, userId);
        tracker.onDisconnect(disconnect("s1"));
        assertThat(tracker.mayDeliver("s1", topic())).isTrue();
    }

    @Test
    void onUnsubscribe_TheClientsOwnUnsubscribe_IsNotRevokedAgain() {
        tracker.onSubscribe(subscribe("s1", "sub-1", topic()));
        tracker.onUnsubscribe(new SessionUnsubscribeEvent(this,
                message(accessor(SimpMessageType.UNSUBSCRIBE, "s1", "sub-1")), user));

        tracker.revoke(chatId, userId);

        verifyNoInteractions(brokerChannel);
    }

    @Test
    void mayDeliver_OtherDestinations_AreAlwaysAllowed() {
        tracker.onSubscribe(subscribe("s1", "sub-1", topic()));
        tracker.revoke(chatId, userId);

        assertThat(tracker.mayDeliver("s1", "/user/queue/messages")).isTrue();
        assertThat(tracker.mayDeliver("s1", "/topic/rooms/" + chatId + "/state")).isTrue();
    }

    @Test
    void onSubscribe_OtherDestinationsOrWithoutUser_AreIgnored() {
        tracker.onSubscribe(subscribe("s1", "sub-1", "/topic/rooms/" + chatId + "/state"));
        tracker.onSubscribe(subscribe("s1", "sub-2", "/topic/broadcast/not-a-uuid/" + broadcastId));
        tracker.onSubscribe(new SessionSubscribeEvent(this, subscribeMessage("s1", "sub-3", topic()), null));

        verifyNoInteractions(broadcastStompGuard);
        tracker.revoke(chatId, userId);
        verifyNoInteractions(brokerChannel);
    }

    @Test
    void revoke_BrokerFailure_IsNotThrown() {
        tracker.onSubscribe(subscribe("s1", "sub-1", topic()));
        doThrow(new IllegalStateException("broker down")).when(brokerChannel).send(any());

        tracker.revoke(chatId, userId);

        assertThat(tracker.mayDeliver("s1", topic())).isFalse();
    }

    private List<String> unsubscribedIds() {
        ArgumentCaptor<Message<?>> sent = ArgumentCaptor.forClass(Message.class);
        verify(brokerChannel, atLeastOnce()).send(sent.capture());
        assertThat(sent.getAllValues()).allSatisfy(message -> assertThat(
                SimpMessageHeaderAccessor.wrap(message).getMessageType()).isEqualTo(SimpMessageType.UNSUBSCRIBE));
        return sent.getAllValues().stream()
                .map(message -> SimpMessageHeaderAccessor.wrap(message).getSubscriptionId())
                .toList();
    }

    private String topic() {
        return "/topic/broadcast/" + chatId + "/" + broadcastId;
    }

    private SessionSubscribeEvent subscribe(String sessionId, String subscriptionId, String destination) {
        return new SessionSubscribeEvent(this, subscribeMessage(sessionId, subscriptionId, destination), user);
    }

    private static Message<byte[]> subscribeMessage(String sessionId, String subscriptionId, String destination) {
        SimpMessageHeaderAccessor accessor = accessor(SimpMessageType.SUBSCRIBE, sessionId, subscriptionId);
        accessor.setDestination(destination);
        return message(accessor);
    }

    private SessionDisconnectEvent disconnect(String sessionId) {
        return new SessionDisconnectEvent(this, message(accessor(SimpMessageType.DISCONNECT, sessionId, null)),
                sessionId, CloseStatus.NORMAL, user);
    }

    private static SimpMessageHeaderAccessor accessor(SimpMessageType type, String sessionId, String subscriptionId) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(type);
        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private static Message<byte[]> message(SimpMessageHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
