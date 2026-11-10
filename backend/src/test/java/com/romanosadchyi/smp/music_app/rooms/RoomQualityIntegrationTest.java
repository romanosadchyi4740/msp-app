package com.romanosadchyi.smp.music_app.rooms;

import com.jayway.jsonpath.JsonPath;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Reported connection quality through the real HTTP layer and STOMP (KAN-48): a participant's round-trip time is
 * stored, shown with the room, and told to the room when it changes.
 */
@IntegrationTest
class RoomQualityIntegrationTest {

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
    void report_FromAParticipant_IsStoredShownAndToldToTheRoomOnceItChanges() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client hostClient = StompTestClient.connect(port, host.bearer());
        sessions.add(hostClient.session());
        BlockingQueue<Map<String, Object>> events = StompTestClient.subscribe(hostClient.session(),
                "/topic/rooms/" + roomId + "/participants");
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Nothing reported yet
        assertThat(participantOf(host, roomId, student)).doesNotContainKey("rttMs");

        report(student, roomId, "{\"rttMs\": 84}").andExpect(status().isNoContent());

        Map<String, Object> event = StompTestClient.pollUntil(events,
                StompTestClient.participantEvent("RTT", student.id()));
        assertThat(event).containsEntry("rttMs", 84).containsEntry("hostId", host.id().toString());
        // Reporting changed nothing else about the participant
        assertThat(participantOf(host, roomId, student)).containsEntry("rttMs", 84)
                .containsEntry("role", "PARTICIPANT");

        // The same value again is not announced
        report(student, roomId, "{\"rttMs\": 84}").andExpect(status().isNoContent());
        assertThat(events.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    void report_InvalidOrFromOutside_IsRejected() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);

        report(student, roomId, "{\"rttMs\": -5}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isString());
        report(student, roomId, "{\"rttMs\": 60001}").andExpect(status().isBadRequest());
        report(student, roomId, "{}").andExpect(status().isBadRequest());
        report(fixtures.register(), roomId, "{\"rttMs\": 84}").andExpect(status().isNotFound());
        mockMvc.perform(put("/api/rooms/" + roomId + "/quality").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rttMs\": 84}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM room_participants WHERE room_id = ? AND rtt_ms IS NOT NULL", Long.class, roomId))
                .isZero();
    }

    /**
     * The participant {@code user} as {@code viewer} sees it in the room, with null fields left out.
     */
    private Map<String, Object> participantOf(Registered viewer, UUID roomId, Registered user) throws Exception {
        String room = mockMvc.perform(get("/api/rooms/" + roomId).header(HttpHeaders.AUTHORIZATION, viewer.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Map<String, Object>> participants = JsonPath.read(room, "$.participants");
        Map<String, Object> participant = participants.stream()
                .filter(candidate -> user.id().toString().equals(candidate.get("userId")))
                .findFirst().orElseThrow();
        Map<String, Object> present = new HashMap<>(participant);
        present.values().removeIf(Objects::isNull);
        return present;
    }

    private ResultActions report(Registered user, UUID roomId, String body) throws Exception {
        return mockMvc.perform(put("/api/rooms/" + roomId + "/quality")
                .header(HttpHeaders.AUTHORIZATION, user.bearer())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
