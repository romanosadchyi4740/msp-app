package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.StompDestinationGuard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompAuthorizationInterceptorTest {

    static final String ROOM_TOPIC = "/topic/rooms/1b4e28ba-2fa1-11d2-883f-0016d3cca427/state";

    @Mock
    StompDestinationGuard roomGuard;

    @Mock
    MessageChannel channel;

    StompAuthorizationInterceptor interceptor;

    AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER");

    @BeforeEach
    void setUp() {
        interceptor = new StompAuthorizationInterceptor(List.of(roomGuard));
        lenient().when(roomGuard.guards(anyString())).thenAnswer(call -> call.<String>getArgument(0)
                .contains("/rooms/"));
    }

    @Test
    void preSend_SubscribeAsParticipant_PassesThrough() {
        when(roomGuard.mayAccess(user.id(), ROOM_TOPIC)).thenReturn(true);
        Message<byte[]> frame = frame(StompCommand.SUBSCRIBE, ROOM_TOPIC, user);

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
    }

    @Test
    void preSend_SubscribeAsNonParticipant_Throws() {
        when(roomGuard.mayAccess(user.id(), ROOM_TOPIC)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, ROOM_TOPIC, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preSend_SendAsNonParticipant_Throws() {
        String command = "/app/rooms/1b4e28ba-2fa1-11d2-883f-0016d3cca427/command";
        when(roomGuard.mayAccess(user.id(), command)).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, command, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preSend_AnonymousOnGuardedDestination_Throws() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, ROOM_TOPIC, null), channel))
                .isInstanceOf(AccessDeniedException.class);
        verify(roomGuard, never()).mayAccess(any(), anyString());
    }

    @Test
    void preSend_UnguardedDestination_PassesThrough() {
        Message<byte[]> frame = frame(StompCommand.SUBSCRIBE, "/user/queue/messages", user);

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
        verify(roomGuard, never()).mayAccess(any(), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/rooms/1b4e28ba-2fa1-11d2-883f-0016d3cca427/state", "/user/abc/queue/messages",
            "/queue/messages", "/chat"})
    void preSend_SendToBrokerDestination_Throws(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, destination, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/topic/rooms/**", "/topic/rooms/*/state", "/topic/broadcast/?", "/topic/{id}"})
    void preSend_PatternSubscription_Throws(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/app//broadcast/1b4e28ba-2fa1-11d2-883f-0016d3cca427/1b4e28ba-2fa1-11d2-883f-0016d3cca428/sync",
            "/app//rooms/1b4e28ba-2fa1-11d2-883f-0016d3cca427/command", "/topic/rooms/", "//topic/rooms/x/state", "/",
            "/app/rooms//command"})
    void preSend_MalformedDestination_Throws(String destination) {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, destination, user), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, destination, user), channel))
                .isInstanceOf(AccessDeniedException.class);
        verify(roomGuard, never()).mayAccess(any(), anyString());
    }

    @Test
    void preSend_NoDestination_Throws() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void preSend_OtherCommands_PassThrough() {
        Message<byte[]> frame = frame(StompCommand.UNSUBSCRIBE, null, null);

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
    }

    private static Message<byte[]> frame(StompCommand command, String destination, AuthenticatedUser user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        if (user != null) {
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
