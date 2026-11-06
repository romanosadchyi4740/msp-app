package com.romanosadchyi.smp.music_app.sync;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommandType;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import com.romanosadchyi.smp.music_app.sync.internal.service.PlaybackStateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Commands applied to a lesson's playback against a real database (KAN-39): the time signature comes from the tab,
 * every change raises the version, a command that changes nothing does not, a rejected one leaves the row alone, and
 * a lesson change waits for a command that holds the row instead of failing on the version.
 */
@IntegrationTest
class PlaybackStateIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    PlaybackStateService playbackStateService;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    PlatformTransactionManager transactionManager;

    UUID senderId = UUID.randomUUID();

    @Test
    void apply_ChangesRaiseTheVersionAndNoOpsDoNot() throws Exception {
        UUID sessionId = startLesson("6/8").sessionId();

        PlaybackStateDto initial = playbackStateService.snapshot(sessionId);
        assertThat(initial.beatsPerMeasure()).isEqualTo(6);
        assertThat(initial.beatUnit()).isEqualTo(8);
        assertThat(initial.status()).isEqualTo(PlaybackStatus.STOPPED);

        PlaybackStateDto playing = playbackStateService.apply(sessionId, command(SyncCommandType.PLAY, null));
        assertThat(playing.status()).isEqualTo(PlaybackStatus.PLAYING);
        assertThat(playing.anchorTime()).isGreaterThan(playing.serverTime());
        assertThat(playing.version()).isEqualTo(initial.version() + 1);

        PlaybackStateDto paused = playbackStateService.apply(sessionId, command(SyncCommandType.PAUSE, null));
        assertThat(paused.status()).isEqualTo(PlaybackStatus.PAUSED);
        assertThat(paused.version()).isEqualTo(initial.version() + 2);

        PlaybackStateDto pausedAgain = playbackStateService.apply(sessionId, command(SyncCommandType.PAUSE, null));
        assertThat(pausedAgain.version()).isEqualTo(paused.version());

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, version, beats_per_measure, beat_unit FROM playback_states WHERE session_id = ?",
                sessionId);
        assertThat(row).containsEntry("status", "PAUSED").containsEntry("beats_per_measure", 6)
                .containsEntry("beat_unit", 8);
        assertThat(((Number) row.get("version")).longValue()).isEqualTo(paused.version());
    }

    @Test
    void apply_InvalidCommand_LeavesTheRowAlone() throws Exception {
        UUID sessionId = startLesson(null).sessionId();
        PlaybackStateDto before = playbackStateService.snapshot(sessionId);

        assertThatThrownBy(() -> playbackStateService.apply(sessionId, command(SyncCommandType.SET_TEMPO, "1000")))
                .isInstanceOf(IllegalArgumentException.class);

        PlaybackStateDto after = playbackStateService.snapshot(sessionId);
        assertThat(after.version()).isEqualTo(before.version());
        assertThat(after.tempoBpm()).isEqualTo(before.tempoBpm());
        assertThat(after.beatsPerMeasure()).isEqualTo(4);
    }

    @Test
    void endLesson_WhileACommandHoldsTheRow_WaitsAndSucceeds() throws Exception {
        Lesson lesson = startLesson("4/4");
        CountDownLatch commandApplied = new CountDownLatch(1);
        CountDownLatch releaseCommand = new CountDownLatch(1);

        CompletableFuture<Void> command = CompletableFuture.runAsync(() ->
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    playbackStateService.apply(lesson.sessionId(), command(SyncCommandType.PLAY, null));
                    commandApplied.countDown();
                    await(releaseCommand);
                }));
        await(commandApplied);

        CompletableFuture<Integer> end = CompletableFuture.supplyAsync(() -> {
            try {
                return mockMvc.perform(post("/api/rooms/" + lesson.roomId() + "/sessions/current/end")
                                .header(HttpHeaders.AUTHORIZATION, lesson.bearer()))
                        .andReturn().getResponse().getStatus();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // Give the end request time to reach the row lock before the command commits
        Thread.sleep(500);
        assertThat(end).isNotDone();
        releaseCommand.countDown();

        command.get(10, TimeUnit.SECONDS);
        assertThat(end.get(10, TimeUnit.SECONDS)).isEqualTo(200);
        assertThat(playbackStateService.snapshot(lesson.sessionId()).status()).isEqualTo(PlaybackStatus.STOPPED);
    }

    private SyncCommand command(SyncCommandType type, String argument) {
        return new SyncCommand(type, argument, senderId, null);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /**
     * Registers a host, creates a room and starts a lesson with a new tab of that time signature.
     */
    private Lesson startLesson(String timeSignature) throws Exception {
        String registered = mockMvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "%s@example.com", "password": "secret123", "displayName": "Host"}
                        """.formatted(UUID.randomUUID())))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID hostId = UUID.fromString(JsonPath.read(registered, "$.userId"));
        String bearer = "Bearer " + JsonPath.read(registered, "$.accessToken");

        String room = mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\": \"Lesson\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID roomId = UUID.fromString(JsonPath.read(room, "$.id"));

        // Tabs belong to another module, so the test writes the row directly rather than through its services
        UUID tabId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tabs (id, owner_id, format, title, raw_text, tempo_bpm, time_signature, is_public,
                                  rights_confirmed, created_at, updated_at)
                VALUES (?, ?, 'ASCII', 'Etude', 'e|---0---|', 120, ?, FALSE, TRUE, now(), now())
                """, tabId, hostId, timeSignature);

        String lesson = mockMvc.perform(post("/api/rooms/" + roomId + "/sessions").header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tabId\": \"" + tabId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new Lesson(UUID.fromString(JsonPath.read(lesson, "$.id")), roomId, bearer);
    }

    private record Lesson(UUID sessionId, UUID roomId, String bearer) {
    }
}
