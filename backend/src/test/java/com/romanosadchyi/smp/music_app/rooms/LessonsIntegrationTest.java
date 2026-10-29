package com.romanosadchyi.smp.music_app.rooms;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.rooms.internal.service.LessonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The lesson lifecycle against a real database (KAN-35): start, tab change and end over REST with the playback state
 * that {@code sync} keeps, host-only rules, the limit, closing the room, and the cascade when a room is deleted.
 */
@IntegrationTest
class LessonsIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    LessonService lessonService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void lesson_StartChangeTabEnd_KeepsThePlaybackStateInStep() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        UUID firstTab = insertTab(host.id(), 90, false);
        UUID secondTab = insertTab(host.id(), null, false);

        String started = mockMvc.perform(post("/api/rooms/" + room.id() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(tabBody(firstTab)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roomId").value(room.id().toString()))
                .andExpect(jsonPath("$.tabId").value(firstTab.toString()))
                .andExpect(jsonPath("$.limitMinutes").value(40))
                .andExpect(jsonPath("$.endsAt").isString())
                .andReturn().getResponse().getContentAsString();
        UUID sessionId = UUID.fromString(JsonPath.read(started, "$.id"));
        Map<String, Object> state = playbackState(sessionId);
        assertThat(state).containsEntry("status", "STOPPED").containsEntry("measure", 1).containsEntry("tempo_bpm", 90);
        long firstVersion = ((Number) state.get("version")).longValue();

        mockMvc.perform(put("/api/rooms/" + room.id() + "/sessions/current/tab")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(tabBody(secondTab)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tabId").value(secondTab.toString()));
        state = playbackState(sessionId);
        assertThat(state).containsEntry("tempo_bpm", 120);
        assertThat(((Number) state.get("version")).longValue()).isGreaterThan(firstVersion);

        mockMvc.perform(get("/api/rooms/" + room.id() + "/sessions/current").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sessionId.toString()));
        mockMvc.perform(post("/api/rooms/" + room.id() + "/sessions/current/end")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endReason").value("HOST_ENDED"))
                .andExpect(jsonPath("$.endedAt").isString());
        mockMvc.perform(get("/api/rooms/" + room.id() + "/sessions/current").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void start_ParticipantOrSecondLessonOrOthersPrivateTab_IsRejected() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        Registered student = register();
        join(student, room);
        UUID tab = insertTab(host.id(), 100, false);
        UUID othersPrivateTab = insertTab(student.id(), 100, false);

        mockMvc.perform(post("/api/rooms/" + room.id() + "/sessions").header(HttpHeaders.AUTHORIZATION, student.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(tabBody(tab)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/rooms/" + room.id() + "/sessions").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(tabBody(othersPrivateTab)))
                .andExpect(status().isNotFound());
        startLesson(host, room, tab);
        mockMvc.perform(post("/api/rooms/" + room.id() + "/sessions").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(tabBody(tab)))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/rooms/" + room.id() + "/sessions/current").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void start_PublicTabOfAnotherUser_IsAllowed() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        UUID publicTab = insertTab(register().id(), 80, true);

        startLesson(host, room, publicTab);
    }

    @Test
    void checkLimit_FreeLessonPastItsLimit_Ends() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        UUID sessionId = startLesson(host, room, insertTab(host.id(), 90, false));
        jdbcTemplate.update("UPDATE lesson_sessions SET started_at = now() - interval '41 minutes' WHERE id = ?",
                sessionId);

        assertThat(lessonService.findRoomsDueForLimitCheck()).contains(room.id());
        lessonService.checkLimit(room.id());

        assertThat(jdbcTemplate.queryForObject("SELECT end_reason FROM lesson_sessions WHERE id = ?", String.class,
                sessionId)).isEqualTo("LIMIT_REACHED");
        assertThat(playbackState(sessionId)).containsEntry("status", "STOPPED");
    }

    @Test
    void checkLimit_FiveMinutesBefore_SetsTheWarningFlag() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        UUID sessionId = startLesson(host, room, insertTab(host.id(), 90, false));
        jdbcTemplate.update("UPDATE lesson_sessions SET started_at = now() - interval '36 minutes' WHERE id = ?",
                sessionId);

        lessonService.checkLimit(room.id());

        assertThat(jdbcTemplate.queryForObject("SELECT limit_warning_sent FROM lesson_sessions WHERE id = ?",
                Boolean.class, sessionId)).isTrue();
        assertThat(jdbcTemplate.queryForObject("SELECT ended_at IS NULL FROM lesson_sessions WHERE id = ?",
                Boolean.class, sessionId)).isTrue();
    }

    @Test
    void closeRoom_EndsTheLessonAndRejectsNewOnes() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        UUID tab = insertTab(host.id(), 90, false);
        UUID sessionId = startLesson(host, room, tab);

        mockMvc.perform(post("/api/rooms/" + room.id() + "/close").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));

        assertThat(jdbcTemplate.queryForObject("SELECT end_reason FROM lesson_sessions WHERE id = ?", String.class,
                sessionId)).isEqualTo("ROOM_CLOSED");
        mockMvc.perform(post("/api/rooms/" + room.id() + "/sessions").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(tabBody(tab)))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/rooms/" + room.id() + "/close").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk());
    }

    @Test
    void closeRoom_Participant_IsForbidden() throws Exception {
        Room room = createRoom(register());
        Registered student = register();
        join(student, room);

        mockMvc.perform(post("/api/rooms/" + room.id() + "/close").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteRoom_CascadesToLessonsAndPlaybackState() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        UUID sessionId = startLesson(host, room, insertTab(host.id(), 90, false));

        jdbcTemplate.update("DELETE FROM rooms WHERE id = ?", room.id());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_sessions WHERE id = ?", Long.class,
                sessionId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM playback_states WHERE session_id = ?", Long.class,
                sessionId)).isZero();
    }

    @Test
    void deleteAccount_HostDuringALesson_RemovesTheLessonAndItsState() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        join(register(), room);
        UUID sessionId = startLesson(host, room, insertTab(host.id(), 90, false));

        mockMvc.perform(delete("/api/users/me").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isNoContent());

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM lesson_sessions WHERE room_id = ?", Long.class,
                room.id())).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM playback_states WHERE session_id = ?", Long.class,
                sessionId)).isZero();
    }

    @Test
    void deleteTab_LeavesTheLessonWithoutTab() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        UUID tab = insertTab(host.id(), 90, false);
        UUID sessionId = startLesson(host, room, tab);

        jdbcTemplate.update("DELETE FROM tabs WHERE id = ?", tab);

        assertThat(jdbcTemplate.queryForObject("SELECT tab_id IS NULL FROM lesson_sessions WHERE id = ?", Boolean.class,
                sessionId)).isTrue();
    }

    @Test
    void insert_SecondActiveLesson_IsRejected() throws Exception {
        Registered host = register();
        Room room = createRoom(host);
        startLesson(host, room, insertTab(host.id(), 90, false));

        assertThatThrownBy(() -> jdbcTemplate.update(
                "INSERT INTO lesson_sessions (id, room_id, started_at) VALUES (?, ?, now())",
                UUID.randomUUID(), room.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID startLesson(Registered host, Room room, UUID tabId) throws Exception {
        String response = mockMvc.perform(post("/api/rooms/" + room.id() + "/sessions")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content(tabBody(tabId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(JsonPath.read(response, "$.id"));
    }

    private Map<String, Object> playbackState(UUID sessionId) {
        return jdbcTemplate.queryForMap("SELECT * FROM playback_states WHERE session_id = ?", sessionId);
    }

    /**
     * Tabs belong to another module, so the test writes the row directly rather than through its services.
     */
    private UUID insertTab(UUID ownerId, Integer tempoBpm, boolean isPublic) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tabs (id, owner_id, format, title, raw_text, tempo_bpm, is_public, rights_confirmed,
                                  created_at, updated_at)
                VALUES (?, ?, 'ASCII', 'Etude', 'e|---0---|', ?, ?, TRUE, now(), now())
                """, id, ownerId, tempoBpm, isPublic);
        return id;
    }

    private static String tabBody(UUID tabId) {
        return "{\"tabId\": \"" + tabId + "\"}";
    }

    private Room createRoom(Registered host) throws Exception {
        String response = mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lesson\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Room(UUID.fromString(JsonPath.read(response, "$.id")), JsonPath.read(response, "$.inviteCode"));
    }

    private void join(Registered user, Room room) throws Exception {
        mockMvc.perform(post("/api/rooms/join/" + room.inviteCode()).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk());
    }

    private Registered register() throws Exception {
        String body = """
                {"email": "%s@example.com", "password": "secret123", "displayName": "Player"}
                """.formatted(UUID.randomUUID());
        String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Registered(UUID.fromString(JsonPath.read(response, "$.userId")), JsonPath.read(response, "$.accessToken"));
    }

    private record Room(UUID id, String inviteCode) {
    }

    private record Registered(UUID id, String token) {

        String bearer() {
            return "Bearer " + token;
        }
    }
}
