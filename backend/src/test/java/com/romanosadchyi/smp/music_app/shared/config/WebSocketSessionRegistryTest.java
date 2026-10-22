package com.romanosadchyi.smp.music_app.shared.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebSocketSessionRegistryTest {

    @Mock
    ObjectProvider<SimpUserRegistry> userRegistryProvider;

    @Mock
    SimpUserRegistry userRegistry;

    @Mock
    WebSocketHandler handler;

    WebSocketSessionRegistry registry;

    WebSocketHandler decorated;

    final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(userRegistryProvider.getObject()).thenReturn(userRegistry);
        registry = new WebSocketSessionRegistry(userRegistryProvider);
        decorated = registry.decorate(handler);
    }

    @Test
    void closeSessionsOf_ClosesEveryOpenSessionOfTheUser() throws Exception {
        WebSocketSession first = open("s1");
        WebSocketSession second = open("s2");
        WebSocketSession others = open("s3");
        givenUserSessions(userId, "s1", "s2");

        registry.closeSessionsOf(userId);

        verify(first).close(CloseStatus.POLICY_VIOLATION);
        verify(second).close(CloseStatus.POLICY_VIOLATION);
        verify(others, never()).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void closeSessionsOf_ForgetsSessionsThatAlreadyClosed() throws Exception {
        WebSocketSession closed = open("s1");
        decorated.afterConnectionClosed(closed, CloseStatus.NORMAL);
        givenUserSessions(userId, "s1");

        registry.closeSessionsOf(userId);

        verify(closed, never()).close(CloseStatus.POLICY_VIOLATION);
        verify(handler).afterConnectionClosed(closed, CloseStatus.NORMAL);
    }

    @Test
    void closeSessionsOf_UserWithoutSessions_DoesNothing() {
        when(userRegistry.getUser(userId.toString())).thenReturn(null);

        registry.closeSessionsOf(userId);
    }

    @Test
    void closeSessionsOf_CloseFails_ClosesTheOthers() throws Exception {
        WebSocketSession failing = open("s1");
        WebSocketSession busy = open("s2");
        WebSocketSession third = open("s3");
        doThrow(new IOException("gone")).when(failing).close(CloseStatus.POLICY_VIOLATION);
        doThrow(new IllegalStateException("busy writing")).when(busy).close(CloseStatus.POLICY_VIOLATION);
        givenUserSessions(userId, "s1", "s2", "s3");

        registry.closeSessionsOf(userId);

        verify(third).close(CloseStatus.POLICY_VIOLATION);
    }

    @Test
    void closeSessionsOf_UserRegistryUnavailable_DoesNotThrow() {
        when(userRegistryProvider.getObject()).thenThrow(new IllegalStateException("no unique bean"));

        registry.closeSessionsOf(userId);
    }

    private WebSocketSession open(String id) throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        lenient().when(session.getId()).thenReturn(id);
        decorated.afterConnectionEstablished(session);
        verify(handler).afterConnectionEstablished(session);
        return session;
    }

    private void givenUserSessions(UUID user, String... sessionIds) {
        SimpUser simpUser = mock(SimpUser.class);
        Set<SimpSession> simpSessions = new LinkedHashSet<>();
        for (String id : sessionIds) {
            SimpSession simpSession = mock(SimpSession.class);
            when(simpSession.getId()).thenReturn(id);
            simpSessions.add(simpSession);
        }
        when(simpUser.getSessions()).thenReturn(simpSessions);
        when(userRegistry.getUser(user.toString())).thenReturn(simpUser);
    }
}
