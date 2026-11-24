package com.romanosadchyi.smp.music_app.rooms;

import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.RoomTestFixtures;
import com.romanosadchyi.smp.music_app.RoomTestFixtures.Lesson;
import com.romanosadchyi.smp.music_app.RoomTestFixtures.Registered;
import com.romanosadchyi.smp.music_app.StompTestClient;
import com.romanosadchyi.smp.music_app.StompTestClient.Client;
import com.romanosadchyi.smp.music_app.rooms.internal.service.HostAwayService;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommandType;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import com.romanosadchyi.smp.music_app.sync.internal.service.PlaybackStateService;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Presence, host hand-over and leaving over the real REST API and STOMP connections (KAN-36): who has the room open,
 * the host's choice of successor, the hand-over when the host stays away, and leaving, which also ends the leaver's
 * subscriptions to the room's topics.
 */
@IntegrationTest
class RoomPresenceIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlaybackStateService playbackStateService;

    @Autowired
    HostAwayService hostAwayService;

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
    void presence_OpeningAndClosingTheRoom_MarksOnlineAndOffline() throws Exception {
        Lesson lesson = fixtures.startLessonWithStudent();
        Client host = connect(lesson.host());
        BlockingQueue<Map<String, Object>> hostHears = StompTestClient.subscribe(host.session(),
                participantsTopic(lesson));
        Thread.sleep(StompTestClient.SUBSCRIBE_SETTLE_MS);

        Client student = connect(lesson.student());
        StompTestClient.subscribe(student.session(), participantsTopic(lesson));
        StompTestClient.pollUntil(hostHears, StompTestClient.participantEvent("ONLINE", lesson.student().id()));
        assertThat(participant(lesson.roomId(), lesson.student().id())).containsEntry("online", true);

        student.session().disconnect();
        StompTestClient.pollUntil(hostHears, StompTestClient.participantEvent("OFFLINE", lesson.student().id()));
        Map<String, Object> row = participant(lesson.roomId(), lesson.student().id());
        assertThat(row).containsEntry("online", false);
        assertThat(row.get("offline_since")).isNotNull();
    }

    @Test
    void presence_TwoTabsAndOneClosed_StaysOnline() throws Exception {
        Lesson lesson = fixtures.startLessonWithStudent();
        Client firstTab = connect(lesson.student());
        Client secondTab = connect(lesson.student());
        StompTestClient.subscribe(firstTab.session(), participantsTopic(lesson));
        StompTestClient.subscribe(secondTab.session(), participantsTopic(lesson));
        Thread.sleep(StompTestClient.SUBSCRIBE_SETTLE_MS);

        firstTab.session().disconnect();
        Thread.sleep(StompTestClient.SUBSCRIBE_SETTLE_MS);

        assertThat(participant(lesson.roomId(), lesson.student().id())).containsEntry("online", true);
    }

    @Test
    void transferHost_ToAParticipant_HandsOverTheCommands() throws Exception {
        Lesson lesson = fixtures.startLessonWithStudent();
        Client student = connect(lesson.student());
        BlockingQueue<Map<String, Object>> studentHears = StompTestClient.subscribe(student.session(),
                participantsTopic(lesson));
        BlockingQueue<Map<String, Object>> studentState = StompTestClient.subscribe(student.session(),
                "/topic/rooms/" + lesson.roomId() + "/state");
        Thread.sleep(StompTestClient.SUBSCRIBE_SETTLE_MS);

        mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/host")
                        .header(HttpHeaders.AUTHORIZATION, lesson.host().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + lesson.student().id() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hostId").value(lesson.student().id().toString()))
                .andExpect(jsonPath("$.myRole").value("PARTICIPANT"));

        StompTestClient.pollUntil(studentHears,
                StompTestClient.participantEvent("HOST_CHANGED", lesson.student().id()));
        student.session().send("/app/rooms/" + lesson.roomId() + "/command", Map.of("type", "PLAY"));
        assertThat(StompTestClient.pollUntil(studentState, message -> message.containsKey("version")))
                .containsEntry("status", "PLAYING");
        // The old host lost the role, so their command is refused
        mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/sessions/current/end")
                        .header(HttpHeaders.AUTHORIZATION, lesson.host().bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void transferHost_InvalidRequests_AreRejected() throws Exception {
        Lesson lesson = fixtures.startLessonWithStudent();
        String hostTransfer = "/api/rooms/" + lesson.roomId() + "/host";

        mockMvc.perform(post(hostTransfer).header(HttpHeaders.AUTHORIZATION, lesson.host().bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400 BAD_REQUEST"));
        mockMvc.perform(post(hostTransfer).header(HttpHeaders.AUTHORIZATION, lesson.host().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + lesson.host().id() + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(hostTransfer).header(HttpHeaders.AUTHORIZATION, lesson.host().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(hostTransfer).header(HttpHeaders.AUTHORIZATION, lesson.student().bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + lesson.student().id() + "\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(hostTransfer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": \"" + lesson.student().id() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void leave_Participant_FreesThePlaceAndEndsTheirSubscriptions() throws Exception {
        Lesson lesson = fixtures.startLessonWithStudent();
        Client host = connect(lesson.host());
        Client student = connect(lesson.student());
        BlockingQueue<Map<String, Object>> hostHears = StompTestClient.subscribe(host.session(),
                participantsTopic(lesson));
        BlockingQueue<Map<String, Object>> studentState = StompTestClient.subscribe(student.session(),
                "/topic/rooms/" + lesson.roomId() + "/state");
        Thread.sleep(StompTestClient.SUBSCRIBE_SETTLE_MS);

        mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/leave")
                        .header(HttpHeaders.AUTHORIZATION, lesson.student().bearer()))
                .andExpect(status().isNoContent());

        StompTestClient.pollUntil(hostHears, StompTestClient.participantEvent("LEFT", lesson.student().id()));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM room_participants WHERE room_id = ? AND user_id = ?",
                Long.class, lesson.roomId(), lesson.student().id())).isZero();
        // The open subscription no longer hears the room
        host.session().send("/app/rooms/" + lesson.roomId() + "/command", Map.of("type", "PLAY"));
        assertThat(studentState.poll(2, TimeUnit.SECONDS)).isNull();
        assertThat(playbackStateService.snapshot(lesson.sessionId()).status()).isEqualTo(PlaybackStatus.PLAYING);
        mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/leave")
                        .header(HttpHeaders.AUTHORIZATION, lesson.student().bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void leave_HostWithAStudent_HandsTheRoomOverAndPauses() throws Exception {
        Lesson lesson = fixtures.startLessonWithStudent();
        playbackStateService.apply(lesson.sessionId(), new SyncCommand(SyncCommandType.PLAY, null, null, null));

        mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/leave")
                        .header(HttpHeaders.AUTHORIZATION, lesson.host().bearer()))
                .andExpect(status().isNoContent());

        assertThat(hostOf(lesson.roomId())).isEqualTo(lesson.student().id());
        assertThat(playbackStateService.snapshot(lesson.sessionId()).status()).isEqualTo(PlaybackStatus.PAUSED);
    }

    @Test
    void leave_LastOneInTheRoom_ClosesItAndEndsTheLesson() throws Exception {
        Lesson lesson = fixtures.startLessonWithStudent();
        mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/leave")
                        .header(HttpHeaders.AUTHORIZATION, lesson.student().bearer()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/leave")
                        .header(HttpHeaders.AUTHORIZATION, lesson.host().bearer()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM rooms WHERE id = ?", String.class,
                lesson.roomId())).isEqualTo("CLOSED");
        assertThat(jdbcTemplate.queryForObject("SELECT end_reason FROM lesson_sessions WHERE id = ?", String.class,
                lesson.sessionId())).isEqualTo("ROOM_CLOSED");
    }

    @Test
    void hostAway_LongerThanTheLimit_PassesTheRoomToAnOnlineStudentAndPauses() throws Exception {
        Lesson lesson = fixtures.startLessonWithStudent();
        Client host = connect(lesson.host());
        StompTestClient.subscribe(host.session(), participantsTopic(lesson));
        Client student = connect(lesson.student());
        BlockingQueue<Map<String, Object>> studentHears = StompTestClient.subscribe(student.session(),
                participantsTopic(lesson));
        Thread.sleep(StompTestClient.SUBSCRIBE_SETTLE_MS);
        playbackStateService.apply(lesson.sessionId(), new SyncCommand(SyncCommandType.PLAY, null, null, null));

        host.session().disconnect();
        StompTestClient.pollUntil(studentHears, StompTestClient.participantEvent("OFFLINE", lesson.host().id()));
        // Not yet away long enough
        assertThat(hostAwayService.findRoomsWithHostAway()).doesNotContain(lesson.roomId());
        jdbcTemplate.update("UPDATE room_participants SET offline_since = now() - interval '2 minutes' "
                + "WHERE room_id = ? AND user_id = ?", lesson.roomId(), lesson.host().id());

        assertThat(hostAwayService.findRoomsWithHostAway()).contains(lesson.roomId());
        hostAwayService.handOver(lesson.roomId());

        assertThat(hostOf(lesson.roomId())).isEqualTo(lesson.student().id());
        assertThat(playbackStateService.snapshot(lesson.sessionId()).status()).isEqualTo(PlaybackStatus.PAUSED);
        StompTestClient.pollUntil(studentHears,
                StompTestClient.participantEvent("HOST_CHANGED", lesson.student().id()));
    }

    private Client connect(Registered user) throws Exception {
        Client client = StompTestClient.connect(port, user.bearer());
        sessions.add(client.session());
        return client;
    }

    private Map<String, Object> participant(UUID roomId, UUID userId) {
        return jdbcTemplate.queryForMap("SELECT online, offline_since FROM room_participants "
                + "WHERE room_id = ? AND user_id = ?", roomId, userId);
    }

    private UUID hostOf(UUID roomId) {
        return jdbcTemplate.queryForObject("SELECT host_id FROM rooms WHERE id = ?", UUID.class, roomId);
    }

    private static String participantsTopic(Lesson lesson) {
        return "/topic/rooms/" + lesson.roomId() + "/participants";
    }
}
