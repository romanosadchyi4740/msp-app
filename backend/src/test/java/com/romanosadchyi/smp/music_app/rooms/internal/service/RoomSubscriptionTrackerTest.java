package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomSubscriptionTrackerTest {

    @Mock
    RoomPresenceService roomPresenceService;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    MessageChannel brokerChannel;

    RoomSubscriptionTracker tracker;

    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    Principal user = new UsernamePasswordAuthenticationToken(
            new AuthenticatedUser(userId, "a@b.c", "ROLE_USER"), null, List.of());

    @BeforeEach
    void setUp() {
        tracker = new RoomSubscriptionTracker(roomPresenceService, participantRepository, messagingTemplate);
        lenient().when(messagingTemplate.getMessageChannel()).thenReturn(brokerChannel);
        lenient().when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(new RoomParticipant()));
    }

    @Test
    void onSubscribe_ParticipantsTopic_MarksOnlineOncePerUser() {
        tracker.onSubscribe(subscribe("s1", "sub-1", participantsTopic()));
        tracker.onSubscribe(subscribe("s2", "sub-1", participantsTopic()));

        verify(roomPresenceService, times(1)).markOnline(roomId, userId);
    }

    @Test
    void onSubscribe_OtherRoomTopicOrDestination_DoesNotCountForPresence() {
        tracker.onSubscribe(subscribe("s1", "sub-1", stateTopic()));
        tracker.onSubscribe(subscribe("s1", "sub-2", "/user/queue/errors"));
        tracker.onSubscribe(subscribe("s1", "sub-3", "/topic/broadcast/x/y"));

        verifyNoInteractions(roomPresenceService);
    }

    @Test
    void onSubscribe_NoLongerAParticipant_UnsubscribesAndStopsDelivery() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        tracker.onSubscribe(subscribe("s1", "sub-1", stateTopic()));

        assertThat(unsubscribedIds()).containsExactly("sub-1");
        assertThat(tracker.mayDeliver("s1", stateTopic())).isFalse();
        verifyNoInteractions(roomPresenceService);
    }

    @Test
    void onDisconnect_OneOfTwoSessions_KeepsTheUserOnline() {
        tracker.onSubscribe(subscribe("s1", "sub-1", participantsTopic()));
        tracker.onSubscribe(subscribe("s2", "sub-1", participantsTopic()));

        tracker.onDisconnect(disconnect("s1"));
        verify(roomPresenceService, never()).markOffline(any(), any());

        tracker.onDisconnect(disconnect("s2"));
        verify(roomPresenceService).markOffline(roomId, userId);
    }

    @Test
    void onUnsubscribe_LastParticipantsSubscription_MarksOffline() {
        tracker.onSubscribe(subscribe("s1", "sub-1", participantsTopic()));
        tracker.onSubscribe(subscribe("s1", "sub-2", stateTopic()));

        tracker.onUnsubscribe(unsubscribe("s1", "sub-2"));
        verify(roomPresenceService, never()).markOffline(any(), any());

        tracker.onUnsubscribe(unsubscribe("s1", "sub-1"));
        verify(roomPresenceService).markOffline(roomId, userId);
    }

    @Test
    void onUnsubscribe_UnknownSubscription_IsIgnored() {
        tracker.onUnsubscribe(unsubscribe("s1", "sub-9"));

        verifyNoInteractions(roomPresenceService);
    }

    @Test
    void revoke_EndsTheUsersRoomSubscriptionsAndStopsDelivery() {
        UUID otherRoom = UUID.randomUUID();
        when(participantRepository.findByRoomIdAndUserId(otherRoom, userId))
                .thenReturn(Optional.of(new RoomParticipant()));
        tracker.onSubscribe(subscribe("s1", "sub-1", participantsTopic()));
        tracker.onSubscribe(subscribe("s1", "sub-2", stateTopic()));
        tracker.onSubscribe(subscribe("s1", "sub-3", "/topic/rooms/" + otherRoom + "/state"));

        tracker.revoke(roomId, userId);

        assertThat(unsubscribedIds()).containsExactlyInAnyOrder("sub-1", "sub-2");
        assertThat(tracker.mayDeliver("s1", stateTopic())).isFalse();
        assertThat(tracker.mayDeliver("s1", "/topic/rooms/" + otherRoom + "/state")).isTrue();
        assertThat(tracker.mayDeliver("s2", stateTopic())).isTrue();
        // The revoked subscriptions are gone, so a later disconnect stores no presence for that room
        tracker.onDisconnect(disconnect("s1"));
        verify(roomPresenceService, never()).markOffline(roomId, userId);
    }

    @Test
    void mayDeliver_AfterJoiningAgainOrDisconnecting_IsAllowedAgain() {
        tracker.onSubscribe(subscribe("s1", "sub-1", stateTopic()));
        tracker.revoke(roomId, userId);

        tracker.onSubscribe(subscribe("s1", "sub-2", stateTopic()));
        assertThat(tracker.mayDeliver("s1", stateTopic())).isTrue();

        tracker.revoke(roomId, userId);
        tracker.onDisconnect(disconnect("s1"));
        assertThat(tracker.mayDeliver("s1", stateTopic())).isTrue();
    }

    @Test
    void mayDeliver_OtherDestinations_AreAlwaysAllowed() {
        tracker.onSubscribe(subscribe("s1", "sub-1", stateTopic()));
        tracker.revoke(roomId, userId);

        assertThat(tracker.mayDeliver("s1", "/user/queue/errors")).isTrue();
        assertThat(tracker.mayDeliver("s1", "/topic/broadcast/x/y")).isTrue();
    }

    @Test
    void presenceFailure_DoesNotReachTheBroker() {
        doThrow(new IllegalStateException("database down")).when(roomPresenceService).markOnline(roomId, userId);

        assertThatCode(() -> tracker.onSubscribe(subscribe("s1", "sub-1", participantsTopic())))
                .doesNotThrowAnyException();
    }

    @Test
    void onSubscribe_WithoutUser_IsIgnored() {
        SimpMessageHeaderAccessor accessor = accessor(SimpMessageType.SUBSCRIBE, "s1", "sub-1");
        accessor.setDestination(participantsTopic());

        tracker.onSubscribe(new SessionSubscribeEvent(this, message(accessor), null));

        verifyNoInteractions(roomPresenceService, participantRepository);
        tracker.revoke(roomId, userId);
        verifyNoInteractions(brokerChannel);
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

    private String participantsTopic() {
        return "/topic/rooms/" + roomId + "/participants";
    }

    private String stateTopic() {
        return "/topic/rooms/" + roomId + "/state";
    }

    private SessionSubscribeEvent subscribe(String sessionId, String subscriptionId, String destination) {
        SimpMessageHeaderAccessor accessor = accessor(SimpMessageType.SUBSCRIBE, sessionId, subscriptionId);
        accessor.setDestination(destination);
        return new SessionSubscribeEvent(this, message(accessor), user);
    }

    private SessionUnsubscribeEvent unsubscribe(String sessionId, String subscriptionId) {
        return new SessionUnsubscribeEvent(this,
                message(accessor(SimpMessageType.UNSUBSCRIBE, sessionId, subscriptionId)), user);
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
