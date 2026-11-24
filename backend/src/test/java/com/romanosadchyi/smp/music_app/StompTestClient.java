package com.romanosadchyi.smp.music_app;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.UtilityClass;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A real STOMP client for integration tests, over the plain WebSocket endpoint {@code /ws-raw} of the server that
 * {@link IntegrationTest} starts on a random port. Payloads are read as JSON maps.
 */
@UtilityClass
public class StompTestClient {

    /**
     * Subscriptions are registered asynchronously; this gives the broker time before a test acts on them.
     */
    public final long SUBSCRIBE_SETTLE_MS = 500;

    /**
     * @param bearer the {@code Authorization} header value, or null to connect without one
     */
    public Client connect(int port, String bearer) throws Exception {
        BlockingQueue<StompHeaders> errors = new LinkedBlockingQueue<>();
        StompHeaders connectHeaders = new StompHeaders();
        if (bearer != null) {
            connectHeaders.add("Authorization", bearer);
        }
        StompSession session = stompClient()
                .connectAsync(url(port), new WebSocketHttpHeaders(), connectHeaders, new ErrorCollector(errors))
                .get(10, TimeUnit.SECONDS);
        return new Client(session, errors);
    }

    /**
     * Starts connecting without waiting, for a connection the server is expected to refuse; its ERROR frames go to
     * the returned queue.
     */
    public BlockingQueue<StompHeaders> connectExpectingRefusal(int port, String bearer) {
        BlockingQueue<StompHeaders> errors = new LinkedBlockingQueue<>();
        StompHeaders connectHeaders = new StompHeaders();
        if (bearer != null) {
            connectHeaders.add("Authorization", bearer);
        }
        stompClient().connectAsync(url(port), new WebSocketHttpHeaders(), connectHeaders, new ErrorCollector(errors));
        return errors;
    }

    /**
     * Disconnects the sessions a test opened; a session the server has closed meanwhile, e.g. after refusing a
     * frame, is skipped.
     */
    public void disconnectAll(List<StompSession> sessions) {
        for (StompSession session : sessions) {
            try {
                if (session.isConnected()) {
                    session.disconnect();
                }
            } catch (RuntimeException e) {
                // Closed by the server between the check and the DISCONNECT frame
            }
        }
    }

    public BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        });
        return received;
    }

    /**
     * The next message, failing the test if none arrives within 5 seconds.
     */
    public Map<String, Object> poll(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        Map<String, Object> message = queue.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("a message within 5 s").isNotNull();
        return message;
    }

    /**
     * The first message matching {@code wanted} within 5 seconds, skipping others.
     */
    public Map<String, Object> pollUntil(BlockingQueue<Map<String, Object>> queue,
                                         Predicate<Map<String, Object>> wanted) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> message = queue.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (message != null && wanted.test(message)) {
                return message;
            }
        }
        throw new AssertionError("No matching message within 5 s");
    }

    /**
     * A participant event of the given type about the given user, as sent to the participants topic.
     */
    public Predicate<Map<String, Object>> participantEvent(String type, UUID userId) {
        return message -> type.equals(message.get("type")) && userId.toString().equals(message.get("userId"));
    }

    private WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private String url(int port) {
        return "ws://localhost:" + port + "/ws-raw";
    }

    /**
     * @param errors the ERROR frames (and transport errors) of the session, e.g. after the server refused a frame
     */
    public record Client(StompSession session, BlockingQueue<StompHeaders> errors) {
    }

    /**
     * Collects the ERROR frames the server sends before it closes a refused session.
     */
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private class ErrorCollector extends StompSessionHandlerAdapter {

        BlockingQueue<StompHeaders> errors;

        @Override
        public void handleFrame(StompHeaders headers, Object payload) {
            errors.add(headers);
        }

        @Override
        public void handleTransportError(StompSession session, Throwable exception) {
            StompHeaders headers = new StompHeaders();
            headers.set("transport-error", String.valueOf(exception.getMessage()));
            errors.add(headers);
        }

        @Override
        public void handleException(StompSession session, StompCommand command, StompHeaders headers,
                                    byte[] payload, Throwable exception) {
            errors.add(headers);
        }
    }
}
