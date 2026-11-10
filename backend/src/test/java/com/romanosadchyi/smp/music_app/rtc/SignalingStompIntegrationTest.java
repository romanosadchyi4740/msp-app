package com.romanosadchyi.smp.music_app.rtc;

import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.RoomTestFixtures;
import com.romanosadchyi.smp.music_app.RoomTestFixtures.Registered;
import com.romanosadchyi.smp.music_app.StompTestClient;
import com.romanosadchyi.smp.music_app.StompTestClient.Client;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The WebRTC signaling relay over a real STOMP connection (KAN-45): a signal reaches only the participant it names,
 * under the sender's real id, and nobody outside the room can send or receive one.
 */
@IntegrationTest
class SignalingStompIntegrationTest {

    static final long SUBSCRIBE_SETTLE_MS = StompTestClient.SUBSCRIBE_SETTLE_MS;

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    RoomTestFixtures fixtures;

    List<StompSession> sessions = new ArrayList<>();

    @BeforeEach
    void setUp() {
        fixtures = new RoomTestFixtures(mockMvc, jdbcTemplate);
    }

    @AfterEach
    void disconnect() {
        StompTestClient.disconnectAll(sessions);
    }

    @Test
    void signal_BetweenParticipants_ReachesOnlyTheRecipientUnderTheSendersRealId() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        Registered outsider = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client hostClient = connect(host.bearer());
        Client studentClient = connect(student.bearer());
        Client outsiderClient = connect(outsider.bearer());
        BlockingQueue<Map<String, Object>> studentSignals = signals(studentClient);
        BlockingQueue<Map<String, Object>> hostSignals = signals(hostClient);
        BlockingQueue<Map<String, Object>> outsiderSignals = signals(outsiderClient);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // The claimed sender and room are forged; the server replaces both
        hostClient.session().send(signalDestination(roomId), Map.of("type", "OFFER", "toId", student.id().toString(),
                "fromId", outsider.id().toString(), "roomId", UUID.randomUUID().toString(), "payload", "sdp-offer"));

        Map<String, Object> signal = StompTestClient.poll(studentSignals);
        assertThat(signal).containsEntry("type", "OFFER")
                .containsEntry("fromId", host.id().toString())
                .containsEntry("toId", student.id().toString())
                .containsEntry("roomId", roomId.toString())
                .containsEntry("payload", "sdp-offer");
        assertThat(hostSignals.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
        assertThat(outsiderSignals).isEmpty();
        assertThat(hostClient.errors()).isEmpty();
    }

    @Test
    void signal_ToSomeoneOutsideTheRoom_IsRejectedAndNotDelivered() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        Registered outsider = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client hostClient = connect(host.bearer());
        Client outsiderClient = connect(outsider.bearer());
        BlockingQueue<Map<String, Object>> hostErrors = StompTestClient.subscribe(hostClient.session(),
                "/user/queue/errors");
        BlockingQueue<Map<String, Object>> outsiderSignals = signals(outsiderClient);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        hostClient.session().send(signalDestination(roomId), Map.of("type", "OFFER", "toId", outsider.id().toString(),
                "payload", "sdp-offer"));

        assertThat(StompTestClient.poll(hostErrors)).containsEntry("code", "404 NOT_FOUND");
        assertThat(outsiderSignals.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();

        // An unknown type is a bad request, and the session stays open
        hostClient.session().send(signalDestination(roomId), Map.of("type", "RENEGOTIATE", "toId",
                student.id().toString(), "payload", "x"));
        assertThat(StompTestClient.poll(hostErrors)).containsEntry("code", "400 BAD_REQUEST");
        assertThat(hostClient.errors()).isEmpty();
    }

    @Test
    void signal_FromAnOutsider_IsRefusedAndNotDelivered() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client studentClient = connect(student.bearer());
        Client outsiderClient = connect(fixtures.register().bearer());
        BlockingQueue<Map<String, Object>> studentSignals = signals(studentClient);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        outsiderClient.session().send(signalDestination(roomId), Map.of("type", "OFFER", "toId",
                student.id().toString(), "payload", "sdp-offer"));

        assertThat(outsiderClient.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(studentSignals.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void signal_InAClosedRoom_IsRejectedAndNotDelivered() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client hostClient = connect(host.bearer());
        Client studentClient = connect(student.bearer());
        BlockingQueue<Map<String, Object>> hostErrors = StompTestClient.subscribe(hostClient.session(),
                "/user/queue/errors");
        BlockingQueue<Map<String, Object>> studentSignals = signals(studentClient);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        mockMvc.perform(post("/api/rooms/" + roomId + "/close").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk());

        hostClient.session().send(signalDestination(roomId), Map.of("type", "OFFER", "toId", student.id().toString(),
                "payload", "sdp-offer"));

        assertThat(StompTestClient.poll(hostErrors)).containsEntry("code", "404 NOT_FOUND");
        assertThat(studentSignals.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    private Client connect(String bearer) throws Exception {
        Client client = StompTestClient.connect(port, bearer);
        sessions.add(client.session());
        return client;
    }

    private static BlockingQueue<Map<String, Object>> signals(Client client) {
        return StompTestClient.subscribe(client.session(), "/user/queue/signal");
    }

    private static String signalDestination(UUID roomId) {
        return "/app/rooms/" + roomId + "/signal";
    }
}
