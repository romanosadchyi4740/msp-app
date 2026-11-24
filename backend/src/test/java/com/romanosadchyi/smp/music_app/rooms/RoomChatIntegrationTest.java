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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The room chat (KAN-51, FR-12) over a real STOMP connection and the REST history: participants send and read
 * messages, which are stored only as ciphertext; anyone else can neither read nor send.
 */
@IntegrationTest
class RoomChatIntegrationTest {

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
    void send_Participant_EveryoneGetsItAndOnlyCiphertextIsStored() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client hostClient = connect(host.bearer());
        Client studentClient = connect(student.bearer());
        BlockingQueue<Map<String, Object>> hostHears = StompTestClient.subscribe(hostClient.session(), chatTopic(roomId));
        BlockingQueue<Map<String, Object>> studentHears = StompTestClient.subscribe(studentClient.session(),
                chatTopic(roomId));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        studentClient.session().send(chatDestination(roomId), Map.of("content", "  Can we slow down bar 12?  "));

        Map<String, Object> heard = StompTestClient.poll(hostHears);
        assertThat(heard).containsEntry("content", "Can we slow down bar 12?")
                .containsEntry("authorId", student.id().toString())
                .containsEntry("roomId", roomId.toString())
                .containsKeys("id", "authorName");
        // The live message carries the time it was stored, like the history does
        assertThat(Instant.parse((String) heard.get("sentAt"))).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(StompTestClient.poll(studentHears)).containsEntry("id", heard.get("id"));

        String stored = jdbcTemplate.queryForObject("SELECT content FROM room_messages WHERE room_id = ?",
                String.class, roomId);
        assertThat(stored).startsWith("v2:").doesNotContain("slow down");
    }

    @Test
    void history_Participants_NewestFirstInPages() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client studentClient = connect(student.bearer());
        BlockingQueue<Map<String, Object>> heard = StompTestClient.subscribe(studentClient.session(),
                chatTopic(roomId));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        for (String text : List.of("one", "two", "three")) {
            studentClient.session().send(chatDestination(roomId), Map.of("content", text));
            StompTestClient.poll(heard);
        }

        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").param("pageSize", "2")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].content").value("three"))
                .andExpect(jsonPath("$.content[1].content").value("two"))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.last").value(false));
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").param("pageNo", "1").param("pageSize", "2")
                        .header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("one"))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void history_NonParticipantOrNoToken_IsRefused() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        Registered outsider = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);

        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").header(HttpHeaders.AUTHORIZATION, outsider.bearer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404 NOT_FOUND"));
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").param("pageSize", "500")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A page holds 1 to 100 messages"));
    }

    @Test
    void send_NonParticipant_IsRefusedByTheGuard() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        Registered outsider = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client outsiderClient = connect(outsider.bearer());

        outsiderClient.session().send(chatDestination(roomId), Map.of("content", "let me in"));

        assertThat(outsiderClient.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(count(roomId)).isZero();
    }

    @Test
    void send_Blank_AnswersOnTheChatErrorQueue() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client studentClient = connect(student.bearer());
        BlockingQueue<Map<String, Object>> chatErrors = StompTestClient.subscribe(studentClient.session(),
                "/user/queue/rooms/" + roomId + "/chat-errors");
        BlockingQueue<Map<String, Object>> otherErrors = StompTestClient.subscribe(studentClient.session(),
                "/user/queue/errors");
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        studentClient.session().send(chatDestination(roomId), Map.of("content", "   "));

        assertThat(StompTestClient.poll(chatErrors)).containsEntry("code", "400 BAD_REQUEST")
                .containsEntry("message", "A message needs some text");
        assertThat(otherErrors.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
        assertThat(count(roomId)).isZero();
    }

    @Test
    void leave_FormerParticipant_CanNeitherReadNorHearNorSend() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client hostClient = connect(host.bearer());
        Client studentClient = connect(student.bearer());
        BlockingQueue<Map<String, Object>> hostHears = StompTestClient.subscribe(hostClient.session(), chatTopic(roomId));
        BlockingQueue<Map<String, Object>> studentHears = StompTestClient.subscribe(studentClient.session(),
                chatTopic(roomId));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        studentClient.session().send(chatDestination(roomId), Map.of("content", "bye"));
        StompTestClient.poll(hostHears);
        StompTestClient.poll(studentHears);

        mockMvc.perform(post("/api/rooms/" + roomId + "/leave").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isNoContent());
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isNotFound());
        hostClient.session().send(chatDestination(roomId), Map.of("content", "only for those still here"));
        assertThat(StompTestClient.poll(hostHears)).containsEntry("content", "only for those still here");
        assertThat(studentHears.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
        // Their earlier message stays in the history
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(jsonPath("$.content[1].content").value("bye"))
                .andExpect(jsonPath("$.content[1].authorId").value(student.id().toString()));

        Client again = connect(student.bearer());
        again.session().send(chatDestination(roomId), Map.of("content", "let me back in"));
        assertThat(again.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(count(roomId)).isEqualTo(2);
    }

    @Test
    void closedRoom_HistoryStaysReadableButNothingCanBeSent() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client hostClient = connect(host.bearer());
        BlockingQueue<Map<String, Object>> heard = StompTestClient.subscribe(hostClient.session(), chatTopic(roomId));
        BlockingQueue<Map<String, Object>> chatErrors = StompTestClient.subscribe(hostClient.session(),
                "/user/queue/rooms/" + roomId + "/chat-errors");
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        hostClient.session().send(chatDestination(roomId), Map.of("content", "see you next week"));
        StompTestClient.poll(heard);

        mockMvc.perform(post("/api/rooms/" + roomId + "/close").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().is2xxSuccessful());
        hostClient.session().send(chatDestination(roomId), Map.of("content", "too late"));

        assertThat(StompTestClient.poll(chatErrors)).containsEntry("code", "409 CONFLICT");
        mockMvc.perform(get("/api/rooms/" + roomId + "/messages").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].content").value("see you next week"));
    }

    @Test
    void deleteAccount_RemovesTheirMessagesButNotTheOthers() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        Client hostClient = connect(host.bearer());
        Client studentClient = connect(student.bearer());
        BlockingQueue<Map<String, Object>> heard = StompTestClient.subscribe(hostClient.session(), chatTopic(roomId));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        studentClient.session().send(chatDestination(roomId), Map.of("content", "from the student"));
        StompTestClient.poll(heard);
        hostClient.session().send(chatDestination(roomId), Map.of("content", "from the host"));
        StompTestClient.poll(heard);

        BlockingQueue<Map<String, Object>> studentHears = StompTestClient.subscribe(studentClient.session(),
                chatTopic(roomId));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().is2xxSuccessful());
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // The deleted account's open session hears nothing more
        hostClient.session().send(chatDestination(roomId), Map.of("content", "after the deletion"));
        StompTestClient.poll(heard);
        assertThat(studentHears.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();

        String body = mockMvc.perform(get("/api/rooms/" + roomId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(JsonPath.<List<String>>read(body, "$.content[*].content"))
                .containsExactly("after the deletion", "from the host");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_messages WHERE author_id = ?",
                Integer.class, student.id())).isZero();
    }

    private Client connect(String bearer) throws Exception {
        Client client = StompTestClient.connect(port, bearer);
        sessions.add(client.session());
        return client;
    }

    private int count(UUID roomId) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM room_messages WHERE room_id = ?", Integer.class,
                roomId);
    }

    private static String chatTopic(UUID roomId) {
        return "/topic/rooms/" + roomId + "/chat";
    }

    private static String chatDestination(UUID roomId) {
        return "/app/rooms/" + roomId + "/chat";
    }
}
