package com.romanosadchyi.smp.music_app.sync;

import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.RoomTestFixtures;
import com.romanosadchyi.smp.music_app.RoomTestFixtures.Lesson;
import com.romanosadchyi.smp.music_app.RoomTestFixtures.Registered;
import com.romanosadchyi.smp.music_app.StompTestClient;
import com.romanosadchyi.smp.music_app.StompTestClient.Client;
import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommandType;
import com.romanosadchyi.smp.music_app.sync.internal.service.PlaybackStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
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

    static final long SUBSCRIBE_SETTLE_MS = StompTestClient.SUBSCRIBE_SETTLE_MS;

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlaybackStateService playbackStateService;

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
        BlockingQueue<StompHeaders> errors = StompTestClient.connectExpectingRefusal(port, null);

        assertThat(errors.poll(5, TimeUnit.SECONDS)).isNotNull();
    }

    private Client connect(String bearer) throws Exception {
        Client client = StompTestClient.connect(port, bearer);
        sessions.add(client.session());
        return client;
    }

    private static BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        return StompTestClient.subscribe(session, destination);
    }

    private static Map<String, Object> poll(BlockingQueue<Map<String, Object>> queue) throws InterruptedException {
        return StompTestClient.poll(queue);
    }

    private static Map<String, Object> pollUntil(BlockingQueue<Map<String, Object>> queue,
                                                 Predicate<Map<String, Object>> wanted) throws InterruptedException {
        return StompTestClient.pollUntil(queue, wanted);
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

    private Lesson startLessonWithStudent() throws Exception {
        return fixtures.startLessonWithStudent();
    }

    private Registered register() throws Exception {
        return fixtures.register();
    }
}
