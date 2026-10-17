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
    void sendFromAnAnonymousSession_PassesWithoutStatusCheck() {
        Message<byte[]> frame = frame(StompCommand.SEND, null);

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
        verifyNoInteractions(accountStatus);
    }

    @Test
    void connectWhenTheStatusCheckFails_LeavesSessionAnonymous() {
        AuthenticatedUser user = new AuthenticatedUser(UUID.randomUUID(), "a@b.c", "ROLE_USER");
        when(jwtService.parseToken("good")).thenReturn(user);
        when(accountStatus.isActive(user.id())).thenThrow(new IllegalStateException("database down"));

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good"), channel);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNull();
    }

    @Test
    void connectWithInvalidToken_LeavesSessionAnonymous() {
        when(jwtService.parseToken("expired")).thenThrow(new ExpiredJwtException(null, null, "expired"));

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer expired"), channel);

        assertThat(StompHeaderAccessor.wrap(result).getUser()).isNull();
    }

    @Test
    void otherCommands_AreNotAuthenticated() {
        interceptor.preSend(frame(StompCommand.SEND, "Bearer good"), channel);

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
