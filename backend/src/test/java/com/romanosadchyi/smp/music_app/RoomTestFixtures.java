package com.romanosadchyi.smp.music_app;

import com.jayway.jsonpath.JsonPath;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Users, rooms and lessons for integration tests, made through the real REST API; only tabs, which belong to another
 * module, are written to the database directly.
 */
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomTestFixtures {

    MockMvc mockMvc;
    JdbcTemplate jdbcTemplate;

    public Registered register() throws Exception {
        String response = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s@example.com", "password": "secret123", "displayName": "Player"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Registered(UUID.fromString(JsonPath.read(response, "$.userId")),
                JsonPath.read(response, "$.accessToken"));
    }

    /**
     * A room of {@code host} that {@code student} joined.
     */
    public UUID createRoomWith(Registered host, Registered student) throws Exception {
        String room = mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lesson\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String inviteCode = JsonPath.read(room, "$.inviteCode");
        mockMvc.perform(post("/api/rooms/join/" + inviteCode).header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().isOk());
        return UUID.fromString(JsonPath.read(room, "$.id"));
    }

    /**
     * Starts a lesson in the room with a new tab of the host at 120 BPM.
     */
    public UUID startLesson(Registered host, UUID roomId) throws Exception {
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
        return UUID.fromString(JsonPath.read(lesson, "$.id"));
    }

    /**
     * A host with a room and a running lesson, and a student who joined it.
     */
    public Lesson startLessonWithStudent() throws Exception {
        Registered host = register();
        Registered student = register();
        UUID roomId = createRoomWith(host, student);
        return new Lesson(roomId, startLesson(host, roomId), host, student);
    }

    public record Registered(UUID id, String token) {

        public String bearer() {
            return "Bearer " + token;
        }
    }

    public record Lesson(UUID roomId, UUID sessionId, Registered host, Registered student) {
    }
}
