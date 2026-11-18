package com.romanosadchyi.smp.music_app.shared.config;

import com.romanosadchyi.smp.music_app.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The WebSocket endpoints accept handshakes only from the configured frontend origins (KAN-22); the {@code test}
 * profile allows {@code http://localhost:5173}.
 */
@IntegrationTest
class WebSocketOriginIntegrationTest {

    static final String ALLOWED_ORIGIN = "http://localhost:5173";
    static final String FOREIGN_ORIGIN = "https://evil.example";

    @LocalServerPort
    int port;

    @Test
    void rawHandshake_FromAllowedOrigin_IsAccepted() throws Exception {
        WebSocketSession session = handshake(ALLOWED_ORIGIN).get(10, TimeUnit.SECONDS);
        try {
            assertThat(session.isOpen()).isTrue();
        } finally {
            session.close();
        }
    }

    @Test
    void rawHandshake_FromForeignOrigin_IsRefused() {
        assertThatThrownBy(() -> handshake(FOREIGN_ORIGIN).get(10, TimeUnit.SECONDS))
                .hasStackTraceContaining("403");
    }

    @Test
    void sockJsInfo_FromAllowedOrigin_IsOk() throws Exception {
        assertThat(sockJsInfoStatus(ALLOWED_ORIGIN)).isEqualTo(200);
    }

    @Test
    void sockJsInfo_FromForeignOrigin_IsForbidden() throws Exception {
        assertThat(sockJsInfoStatus(FOREIGN_ORIGIN)).isEqualTo(403);
    }

    private CompletableFuture<WebSocketSession> handshake(String origin) {
        WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
        headers.setOrigin(origin);
        return new StandardWebSocketClient()
                .execute(new TextWebSocketHandler(), headers, URI.create("ws://localhost:" + port + "/ws-raw"));
    }

    private int sockJsInfoStatus(String origin) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/ws/info"))
                    .header("Origin", origin)
                    .GET()
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
        }
    }
}
