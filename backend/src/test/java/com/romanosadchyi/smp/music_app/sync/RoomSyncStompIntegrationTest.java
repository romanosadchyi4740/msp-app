package com.romanosadchyi.smp.music_app.sync;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommandType;
import com.romanosadchyi.smp.music_app.sync.internal.service.PlaybackStateService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The room sync channel over a real STOMP connection (KAN-40, KAN-23): only the host's commands change the playback,
 * every participant hears the new state, snapshots reach joiners and those who ask, and the STOMP guards keep
 * everyone else out, of rooms and of the msp-app listening broadcasts alike.
 */
@IntegrationTest
class RoomSyncStompIntegrationTest {

    /**
     * Subscriptions are registered asynchronously; this gives the broker time before the test acts on them.
     */
    static final long SUBSCRIBE_SETTLE_MS = 500;

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlaybackStateService playbackStateService;

    List<StompSession> sessions = new ArrayList<>();

    @AfterEach
    void disconnect() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    @Test
    void command_FromHostOrParticipant_OnlyTheHostChangesPlayback() throws Exception {
        Lesson lesson = startLessonWithStudent();
        Client student = connect(lesson.student().bearer());
        Client host = connect(lesson.host().bearer());
        BlockingQueue<Map<String, Object>> studentState = subscribe(student.session(), stateTopic(lesson));
        BlockingQueue<Map<String, Object>> studentErrors = subscribe(student.session(), "/user/queue/errors");
        BlockingQueue<Map<String, Object>> studentSnapshots = subscribe(student.session(),
                "/user/queue/rooms/" + lesson.roomId() + "/snapshot");
        BlockingQueue<Map<String, Object>> joinSnapshot = subscribe(student.session(),
                "/app/rooms/" + lesson.roomId() + "/snapshot");
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Joining: the snapshot subscription is answered at once
        Map<String, Object> snapshot = poll(joinSnapshot);
        assertThat(snapshot).containsEntry("sessionId", lesson.sessionId().toString());
        Map<String, Object> playback = map(snapshot.get("playback"));
        assertThat(playback).containsEntry("status", "STOPPED");
        long initialVersion = number(playback.get("version"));

        // A participant's command is refused and changes nothing
        student.session().send(commandDestination(lesson), Map.of("type", "PLAY"));
        assertThat(poll(studentErrors)).containsEntry("code", "403 FORBIDDEN");
        assertThat(studentState.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
        assertThat(playbackStateService.snapshot(lesson.sessionId()).version()).isEqualTo(initialVersion);

        // The host's command reaches the participant
        host.session().send(commandDestination(lesson), Map.of("type", "PLAY"));
        Map<String, Object> playing = poll(studentState);
        assertThat(playing).containsEntry("status", "PLAYING");
        assertThat(number(playing.get("version"))).isEqualTo(initialVersion + 1);
        assertThat(number(playing.get("anchorTime"))).isGreaterThan(number(playing.get("serverTime")));

        // REQUEST_SYNC answers the asking participant with the current state
        student.session().send(commandDestination(lesson), Map.of("type", "REQUEST_SYNC"));
        Map<String, Object> synced = poll(studentSnapshots);
        assertThat(map(synced.get("playback"))).containsEntry("status", "PLAYING");

        // An invalid command is a bad request for the host
        BlockingQueue<Map<String, Object>> hostErrors = subscribe(host.session(), "/user/queue/errors");
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        host.session().send(commandDestination(lesson), Map.of("type", "SEEK", "argument", "zero"));
        assertThat(poll(hostErrors)).containsEntry("code", "400 BAD_REQUEST");
    }

    @Test
    void endLesson_WithListeners_BroadcastsTheFinalState() throws Exception {
        Lesson lesson = startLessonWithStudent();
        Client student = connect(lesson.student().bearer());
        BlockingQueue<Map<String, Object>> studentState = subscribe(student.session(), stateTopic(lesson));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        long playingVersion = playbackStateService.apply(lesson.sessionId(),
                new SyncCommand(SyncCommandType.PLAY, null, lesson.host().id(), null)).version();

        mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/sessions/current/end")
                        .header(HttpHeaders.AUTHORIZATION, lesson.host().bearer()))
                .andExpect(status().isOk());

        // The lesson event and the final playback go to the same topic; the playback is the one with a version
        Map<String, Object> state = pollUntil(studentState, message -> message.containsKey("version"));
        assertThat(state).containsEntry("status", "STOPPED");
        assertThat(number(state.get("version"))).isGreaterThan(playingVersion);
    }

    @Test
    void subscribe_OutsiderToRoomState_IsRefused() throws Exception {
        Lesson lesson = startLessonWithStudent();
        Client outsider = connect(register().bearer());

        subscribe(outsider.session(), stateTopic(lesson));

        assertThat(outsider.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void send_OutsiderCommand_IsRefused() throws Exception {
        Lesson lesson = startLessonWithStudent();
        Client outsider = connect(register().bearer());

        outsider.session().send(commandDestination(lesson), Map.of("type", "PLAY"));

        assertThat(outsider.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
        PlaybackStateDto state = playbackStateService.snapshot(lesson.sessionId());
        assertThat(state.status().name()).isEqualTo("STOPPED");
    }

    @Test
    void send_ParticipantToStateTopic_IsRefused() throws Exception {
        Lesson lesson = startLessonWithStudent();
        Client student = connect(lesson.student().bearer());

        student.session().send(stateTopic(lesson), Map.of("status", "PLAYING", "version", 999));

        assertThat(student.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void subscribe_PatternDestination_IsRefused() throws Exception {
        Lesson lesson = startLessonWithStudent();
        Client student = connect(lesson.student().bearer());

        subscribe(student.session(), "/topic/rooms/**");

        assertThat(student.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
    }

    @Test
    void subscribe_BroadcastOfAChat_OnlyMembersListen() throws Exception {
        Registered master = register();
        Registered member = register();
        Registered outsider = register();
        // Chats belong to another module, so the test writes the rows directly rather than through its services
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO chats (id, is_group, created_at, updated_at) VALUES (?, FALSE, now(), now())",
                chatId);
        for (Registered user : List.of(master, member)) {
            jdbcTemplate.update("""
                    INSERT INTO chat_memberships (id, chat_id, user_id, status, is_admin)
                    VALUES (?, ?, ?, 'ACCEPTED', FALSE)
                    """, UUID.randomUUID(), chatId, user.id());
        }
        String broadcast = chatId + "/" + UUID.randomUUID();
        Client masterClient = connect(master.bearer());
        Client memberClient = connect(member.bearer());
        Client outsiderClient = connect(outsider.bearer());

        BlockingQueue<Map<String, Object>> heard = subscribe(memberClient.session(), "/topic/broadcast/" + broadcast);
        subscribe(outsiderClient.session(), "/topic/broadcast/" + broadcast);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        masterClient.session().send("/app/broadcast/" + broadcast + "/sync", Map.of("command", "PLAY",
                "timestamp", 12.5));

        assertThat(poll(heard)).containsEntry("command", "PLAY");
        assertThat(outsiderClient.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(memberClient.errors()).isEmpty();
    }

    @Test
    void send_DoubleSlashDestination_IsRefused() throws Exception {
        Lesson lesson = startLessonWithStudent();
        Client outsider = connect(register().bearer());

        // The handler mapping drops empty segments, so without the check this would reach the command handler
        outsider.session().send("/app//rooms/" + lesson.roomId() + "/command", Map.of("type", "PLAY"));

        assertThat(outsider.errors().poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(playbackStateService.snapshot(lesson.sessionId()).status().name()).isEqualTo("STOPPED");
    }

    @Test
    void connect_WithoutToken_IsRefused() throws Exception {
        BlockingQueue<StompHeaders> errors = new LinkedBlockingQueue<>();
        WebSocketStompClient client = stompClient();

        client.connectAsync(url(), new WebSocketHttpHeaders(), new StompHeaders(), new ErrorCollector(errors));

        assertThat(errors.poll(5, TimeUnit.SECONDS)).isNotNull();
    }

    private Client connect(String bearer) throws Exception {
        BlockingQueue<StompHeaders> errors = new LinkedBlockingQueue<>();
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", bearer);
        StompSession session = stompClient()
                .connectAsync(url(), new WebSocketHttpHeaders(), connectHeaders, new ErrorCollector(errors))
                .get(10, TimeUnit.SECONDS);
        sessions.add(session);
        return new Client(session, errors);
    }

    private static WebSocketStompClient stompClient() {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        client.setMessageConverter(new JacksonJsonMessageConverter());
        return client;
    }

    private String url() {
        return "ws://localhost:" + port + "/ws-raw";
    }

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
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

    private static Map<String, Object> poll(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        Map<String, Object> message = queue.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("a message within 5 s").isNotNull();
        return message;
    }

    private static Map<String, Object> pollUntil(BlockingQueue<Map<String, Object>> queue,
                                                 Predicate<Map<String, Object>> wanted)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            Map<String, Object> message = queue.poll(deadline - System.currentTimeMillis(), TimeUnit.MILLISECONDS);
            if (message != null && wanted.test(message)) {
                return message;
            }
        }
        throw new AssertionError("No matching message within 5 s");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        assertThat(value).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    private static long number(Object value) {
        return ((Number) value).longValue();
    }

    private static String stateTopic(Lesson lesson) {
        return "/topic/rooms/" + lesson.roomId() + "/state";
    }

    private static String commandDestination(Lesson lesson) {
        return "/app/rooms/" + lesson.roomId() + "/command";
    }

    /**
     * A host with a room and a running lesson, and a student who joined it.
     */
    private Lesson startLessonWithStudent() throws Exception {
        Registered host = register();
        Registered student = register();
        String room = mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lesson\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roomId = UUID.fromString(JsonPath.read(room, "$.id"));
        String inviteCode = JsonPath.read(room, "$.inviteCode");
        mockMvc.perform(post("/api/rooms/join/" + inviteCode).header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk());

        // Tabs belong to another module, so the test writes the row directly rather than through its services
        UUID tabId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tabs (id, owner_id, format, title, raw_text, tempo_bpm, is_public, rights_confirmed,
                                  created_at, updated_at)
                VALUES (?, ?, 'ASCII', 'Etude', 'e|---0---|', 120, FALSE, TRUE, now(), now())
                """, tabId, host.id());
        String lesson = mockMvc.perform(post("/api/rooms/" + roomId + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tabId\": \"" + tabId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Lesson(roomId, UUID.fromString(JsonPath.read(lesson, "$.id")), host, student);
    }

    private Registered register() throws Exception {
        String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s@example.com", "password": "secret123", "displayName": "Player"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Registered(UUID.fromString(JsonPath.read(response, "$.userId")),
                JsonPath.read(response, "$.accessToken"));
    }

    /**
     * Collects the ERROR frames the server sends before it closes a refused session.
     */
    @RequiredArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
    private static class ErrorCollector extends StompSessionHandlerAdapter {

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

    private record Client(StompSession session, BlockingQueue<StompHeaders> errors) {
    }

    private record Lesson(UUID roomId, UUID sessionId, Registered host, Registered student) {
    }

    private record Registered(UUID id, String token) {

        String bearer() {
            return "Bearer " + token;
        }
    }
}
