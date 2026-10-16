package com.romanosadchyi.smp.music_app.shared.config;

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
import org.springframework.security.core.context.SecurityContextHolder;

import java.security.Principal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtStompChannelInterceptorTest {

    @Mock
    JwtService jwtService;

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

        Message<?> result = interceptor.preSend(frame(StompCommand.CONNECT, "Bearer good"), channel);

        Principal sessionUser = StompHeaderAccessor.wrap(result).getUser();
        assertThat(sessionUser).isNotNull();
        assertThat(sessionUser.getName()).isEqualTo(user.id().toString());
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

    private static Message<byte[]> frame(StompCommand command, String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.addNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

}
