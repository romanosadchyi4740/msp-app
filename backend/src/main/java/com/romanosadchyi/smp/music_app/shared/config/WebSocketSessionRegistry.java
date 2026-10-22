package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.shared.security.SessionTerminator;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.WebSocketHandlerDecorator;
import org.springframework.web.socket.handler.WebSocketHandlerDecoratorFactory;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The open WebSocket sessions, so the server can close a user's sessions, e.g. when their account is deleted.
 * Sessions are kept by id; which user a session belongs to is known only after STOMP {@code CONNECT}, so the user's
 * session ids come from the {@link SimpUserRegistry} (a STOMP session id is the WebSocket session id).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WebSocketSessionRegistry implements WebSocketHandlerDecoratorFactory, SessionTerminator {

    // Looked up lazily: the user registry is created by the broker configuration that uses this decorator
    ObjectProvider<SimpUserRegistry> userRegistry;
    Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    @Override
    public @NonNull WebSocketHandler decorate(@NonNull WebSocketHandler handler) {
        return new WebSocketHandlerDecorator(handler) {

            @Override
            public void afterConnectionEstablished(@NonNull WebSocketSession session) throws Exception {
                sessions.put(session.getId(), session);
                super.afterConnectionEstablished(session);
            }

            @Override
            public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus closeStatus)
                    throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, closeStatus);
            }
        };
    }

    /**
     * Closes every open session of the user; the client's reconnect then goes through the CONNECT checks again.
     * Failures are logged, not thrown: the STOMP interceptor still rejects frames of a session left open.
     */
    @Override
    public void closeSessionsOf(UUID userId) {
        SimpUser user;
        try {
            user = userRegistry.getObject().getUser(userId.toString());
        } catch (RuntimeException e) {
            log.warn("Could not look up the WebSocket sessions of user {}: {}", userId, e.getMessage());
            return;
        }
        if (user == null) {
            return;
        }
        for (SimpSession simpSession : user.getSessions()) {
            WebSocketSession session = sessions.get(simpSession.getId());
            if (session != null) {
                close(session);
            }
        }
    }

    private static void close(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not close WebSocket session {}: {}", session.getId(), e.getMessage());
        }
    }
}
