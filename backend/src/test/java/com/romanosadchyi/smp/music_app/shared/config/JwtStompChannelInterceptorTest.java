package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.AccountStatus;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.shared.security.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtStompChannelInterceptorTest {

    @Mock
    JwtService jwtService;

    @Mock
    AccountStatus accountStatus;

    @Mock
    MessageChannel channel;

    @InjectMocks
    JwtStompChannelInterceptor interceptor;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void connectWithValidToken_SetsSessionUserNamedByUserId() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER");
        when(jwtService.parseToken("good")).thenReturn(user);
        when(accountStatus.isActive(user.id())).thenReturn(true);

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good"), channel);

        Principal sessionUser = StompHeaderAccessor.wrap(result).getUser();
        assertThat(sessionUser).isNotNull();
        assertThat(sessionUser.getName()).isEqualTo(user.id().toString());
    }

    @Test
    void stompFrameWithValidToken_SetsSessionUser() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER");
        when(jwtService.parseToken("good")).thenReturn(user);
        when(accountStatus.isActive(user.id())).thenReturn(true);

        Message<?> result = interceptor.preSend(frame(StompCommand.STOMP, "Bearer good"), channel);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNotNull();
    }

    @Test
    void stompFrameWithoutToken_IsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.STOMP, null), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithADeletedAccountsToken_IsRefused() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "deleted@system.local", "ROLE_USER");
        when(jwtService.parseToken("old")).thenReturn(user);
        when(accountStatus.isActive(user.id())).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer old"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sendFromASessionWhoseAccountWasDeleted_IsRejected() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER");
        when(accountStatus.isActive(user.id())).thenReturn(false);

        assertThatThrownBy(() -> interceptor.preSend(sessionFrame(StompCommand.SEND, user), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(sessionFrame(StompCommand.SUBSCRIBE, user), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sendFromAnActiveSession_PassesThrough() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER");
        when(accountStatus.isActive(user.id())).thenReturn(true);
        Message<byte[]> frame = sessionFrame(StompCommand.SEND, user);

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
    }

    @Test
    void sendOrSubscribeFromASessionWithoutUser_IsRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, null), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, null), channel))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(accountStatus);
    }

    @Test
    void connectWhenTheStatusCheckFails_IsRefused() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER");
        when(jwtService.parseToken("good")).thenReturn(user);
        when(accountStatus.isActive(user.id())).thenThrow(new IllegalStateException("database down"));

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithInvalidToken_IsRefused() {
        when(jwtService.parseToken("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Bearer expired"), channel))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void connectWithoutToken_IsRefused() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null), channel))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, "Basic abc"), channel))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(jwtService);
    }

    @Test
    void otherCommands_AreNotAuthenticated() {
        Message<byte[]> frame = frame(StompCommand.DISCONNECT, "Bearer good");

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
        verifyNoInteractions(jwtService);
    }

    private static Message<byte[]> sessionFrame(StompCommand command, AuthenticatedUser user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Message<byte[]> frame(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authorization != null) {
            accessor.addNativeHeader("Authorization", authorization);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
