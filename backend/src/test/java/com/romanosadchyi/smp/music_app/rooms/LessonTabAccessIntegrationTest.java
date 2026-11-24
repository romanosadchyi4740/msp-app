package com.romanosadchyi.smp.music_app.rooms;

import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.RoomTestFixtures;
import com.romanosadchyi.smp.music_app.RoomTestFixtures.Registered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Participants read the tab of their room's active lesson, also the host's private tab (KAN-94); nobody else does,
 * and the access ends with the lesson or with leaving the room.
 */
@IntegrationTest
class LessonTabAccessIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    RoomTestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new RoomTestFixtures(mockMvc, jdbcTemplate);
    }

    @Test
    void privateLessonTab_ParticipantReadsItAndItsFile_OthersDoNot() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        Registered outsider = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        UUID tabId = insertGuitarProTab(host.id());

        expectTab(student, tabId, status().isNotFound());
        startLesson(host, roomId, tabId);

        expectTab(student, tabId, status().isOk());
        expectFileUrl(student, tabId, status().isOk());
        expectTab(outsider, tabId, status().isNotFound());
        expectFileUrl(outsider, tabId, status().isNotFound());
    }

    @Test
    void privateLessonTab_AfterTheLessonEnds_IsPrivateAgain() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        UUID tabId = insertGuitarProTab(host.id());
        startLesson(host, roomId, tabId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/sessions/current/end")
                        .header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().isOk());

        expectTab(student, tabId, status().isNotFound());
        expectFileUrl(student, tabId, status().isNotFound());
    }

    @Test
    void privateLessonTab_AfterTheParticipantLeaves_IsPrivateAgain() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        UUID tabId = insertGuitarProTab(host.id());
        startLesson(host, roomId, tabId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/leave").header(HttpHeaders.AUTHORIZATION, student.bearer()))
                .andExpect(status().is2xxSuccessful());

        expectTab(student, tabId, status().isNotFound());
    }

    @Test
    void privateLessonTab_AfterTheRoomCloses_IsPrivateAgain() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        UUID tabId = insertGuitarProTab(host.id());
        startLesson(host, roomId, tabId);

        mockMvc.perform(post("/api/rooms/" + roomId + "/close").header(HttpHeaders.AUTHORIZATION, host.bearer()))
                .andExpect(status().is2xxSuccessful());

        expectTab(student, tabId, status().isNotFound());
    }

    @Test
    void hiddenLessonTab_StaysHiddenFromParticipants() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        UUID tabId = insertGuitarProTab(host.id());
        startLesson(host, roomId, tabId);

        jdbcTemplate.update("UPDATE tabs SET hidden = TRUE WHERE id = ?", tabId);

        expectTab(student, tabId, status().isNotFound());
        expectTab(host, tabId, status().isOk());
    }

    @Test
    void otherPrivateTabsOfTheHost_StayPrivate() throws Exception {
        Registered host = fixtures.register();
        Registered student = fixtures.register();
        UUID roomId = fixtures.createRoomWith(host, student);
        startLesson(host, roomId, insertGuitarProTab(host.id()));
        UUID otherTab = insertGuitarProTab(host.id());

        expectTab(student, otherTab, status().isNotFound());
    }

    private UUID insertGuitarProTab(UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tabs (id, owner_id, format, title, file_key, gp_version, tempo_bpm, is_public,
                                  rights_confirmed, created_at, updated_at)
                VALUES (?, ?, 'GUITAR_PRO', 'Private etude', ?, 'gp5', 100, FALSE, TRUE, now(), now())
                """, id, ownerId, "tabs/" + id + ".gp5");
        return id;
    }

    private void startLesson(Registered host, UUID roomId, UUID tabId) throws Exception {
        mockMvc.perform(post("/api/rooms/" + roomId + "/sessions").header(HttpHeaders.AUTHORIZATION, host.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tabId\": \"" + tabId + "\"}"))
                .andExpect(status().isCreated());
    }

    private void expectTab(Registered user, UUID tabId, ResultMatcher expected) throws Exception {
        mockMvc.perform(get("/api/tabs/" + tabId).header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(expected);
    }

    private void expectFileUrl(Registered user, UUID tabId, ResultMatcher expected) throws Exception {
        mockMvc.perform(get("/api/tabs/" + tabId + "/url").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(expected);
    }
}
