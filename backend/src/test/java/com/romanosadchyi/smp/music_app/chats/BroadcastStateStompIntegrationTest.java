package com.romanosadchyi.smp.music_app.chats;

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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Listening broadcasts kept on the server (KAN-109): a listener who joins gets the host's state as it is, paused
 * included, without disturbing the others, and only the host can change it (KAN-110).
 */
@IntegrationTest
class BroadcastStateStompIntegrationTest {

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
    void join_WhileTheHostIsPaused_GetsItPausedAndOthersHearNothing() throws Exception {
        Registered host = fixtures.register();
        Registered early = fixtures.register();
        Registered late = fixtures.register();
        UUID chatId = groupChat(host, early, late);
        UUID trackId = track(host);
        String broadcast = chatId + "/" + UUID.randomUUID();
        Client hostClient = connect(host);
        Client earlyClient = connect(early);
        BlockingQueue<Map<String, Object>> earlyHears = StompTestClient.subscribe(earlyClient.session(),
                topic(broadcast));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // The host's track is paused at 1:00 when the broadcast starts (the reported case)
        send(hostClient, broadcast, "START", trackId, 60.0, false);
        assertThat(StompTestClient.poll(earlyHears)).containsEntry("playing", false).containsEntry("position", 60.0);

        Client lateClient = connect(late);
        BlockingQueue<Map<String, Object>> lateHears = StompTestClient.subscribe(lateClient.session(),
                topic(broadcast));
        BlockingQueue<Map<String, Object>> snapshots = StompTestClient.subscribe(lateClient.session(),
                "/user/queue/broadcast/" + broadcast + "/snapshot");
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        send(lateClient, broadcast, "REQUEST_SYNC", null, null, null);

        Map<String, Object> snapshot = StompTestClient.poll(snapshots);
        assertThat(snapshot)
                .containsEntry("status", "ACTIVE")
                .containsEntry("trackId", trackId.toString())
                .containsEntry("playing", false)
                .containsEntry("position", 60.0)
                .containsEntry("hostId", host.id().toString());
        // The snapshot went to the joiner only: neither the topic nor the earlier listener heard anything
        assertThat(lateHears.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
        assertThat(earlyHears.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();

        // The host's next change reaches everyone, with a newer version
        send(hostClient, broadcast, "UPDATE", trackId, 60.0, true);
        Map<String, Object> playing = StompTestClient.poll(lateHears);
        assertThat(playing).containsEntry("playing", true);
        assertThat(((Number) playing.get("version")).longValue())
                .isGreaterThan(((Number) snapshot.get("version")).longValue());
        assertThat(StompTestClient.poll(earlyHears)).containsEntry("playing", true);
    }

    @Test
    void commandsOfAMemberWhoIsNotTheHost_AreRefusedAndChangeNothing() throws Exception {
        Registered host = fixtures.register();
        Registered member = fixtures.register();
        UUID chatId = groupChat(host, member);
        UUID trackId = track(host);
        UUID otherTrack = track(host);
        String broadcast = chatId + "/" + UUID.randomUUID();
        Client hostClient = connect(host);
        Client memberClient = connect(member);
        BlockingQueue<Map<String, Object>> memberHears = StompTestClient.subscribe(memberClient.session(),
                topic(broadcast));
        BlockingQueue<Map<String, Object>> memberErrors = StompTestClient.subscribe(memberClient.session(),
                "/user/queue/broadcast-errors");
        BlockingQueue<Map<String, Object>> snapshots = StompTestClient.subscribe(memberClient.session(),
                "/user/queue/broadcast/" + broadcast + "/snapshot");
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        send(hostClient, broadcast, "START", trackId, 5.0, false);
        StompTestClient.poll(memberHears);

        send(memberClient, broadcast, "UPDATE", otherTrack, 0.0, true);
        send(memberClient, broadcast, "START", otherTrack, 0.0, true);
        send(memberClient, broadcast, "TERMINATE", null, null, null);

        for (int i = 0; i < 3; i++) {
            assertThat(StompTestClient.poll(memberErrors)).containsEntry("code", "403 FORBIDDEN");
        }
        assertThat(memberHears.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
        send(memberClient, broadcast, "REQUEST_SYNC", null, null, null);
        assertThat(StompTestClient.poll(snapshots))
                .containsEntry("status", "ACTIVE")
                .containsEntry("trackId", trackId.toString())
                .containsEntry("playing", false);
    }

    @Test
    void hostCommands_UnknownTrackIsRefused_TerminateEndsItForEveryone() throws Exception {
        Registered host = fixtures.register();
        Registered member = fixtures.register();
        UUID chatId = groupChat(host, member);
        UUID trackId = track(host);
        String broadcast = chatId + "/" + UUID.randomUUID();
        Client hostClient = connect(host);
        Client memberClient = connect(member);
        BlockingQueue<Map<String, Object>> hostErrors = StompTestClient.subscribe(hostClient.session(),
                "/user/queue/broadcast-errors");
        BlockingQueue<Map<String, Object>> memberHears = StompTestClient.subscribe(memberClient.session(),
                topic(broadcast));
        Thread.sleep(SUBSCRIBE_SETTLE_MS);
        send(hostClient, broadcast, "START", trackId, 0.0, true);
        StompTestClient.poll(memberHears);

        send(hostClient, broadcast, "UPDATE", UUID.randomUUID(), 0.0, true);
        assertThat(StompTestClient.poll(hostErrors)).containsEntry("code", "400 BAD_REQUEST");
        assertThat(memberHears.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();

        send(hostClient, broadcast, "TERMINATE", null, null, null);
        assertThat(StompTestClient.poll(memberHears))
                .containsEntry("status", "TERMINATED")
                .containsEntry("playing", false);
    }

    private static String topic(String broadcast) {
        return "/topic/broadcast/" + broadcast;
    }

    private static void send(Client client, String broadcast, String command, UUID trackId, Double position,
                             Boolean playing) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("command", command);
        if (trackId != null) {
            payload.put("trackId", trackId.toString());
        }
        if (position != null) {
            payload.put("position", position);
        }
        if (playing != null) {
            payload.put("playing", playing);
        }
        client.session().send("/app/broadcast/" + broadcast + "/sync", payload);
    }

    /**
     * A track row of the given author; the broadcast only checks that the track exists.
     */
    private UUID track(Registered author) {
        UUID trackId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO tracks (id, created_at, updated_at, duration, file_key, title, author_id)
                VALUES (?, now(), now(), 180, ?, 'Broadcast check', ?)
                """, trackId, "tracks/" + trackId + ".mp3", author.id());
        return trackId;
    }

    /**
     * A group chat of the host (its admin) and members who accepted, written directly as other integration tests do.
     */
    private UUID groupChat(Registered host, Registered... members) {
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO chats (id, is_group, created_at, updated_at) VALUES (?, TRUE, now(), now())",
                chatId);
        insertMembership(chatId, host, true);
        for (Registered member : members) {
            insertMembership(chatId, member, false);
        }
        return chatId;
    }

    private void insertMembership(UUID chatId, Registered user, boolean admin) {
        jdbcTemplate.update("""
                INSERT INTO chat_memberships (id, chat_id, user_id, status, is_admin)
                VALUES (?, ?, ?, 'ACCEPTED', ?)
                """, UUID.randomUUID(), chatId, user.id(), admin);
    }

    private Client connect(Registered user) throws Exception {
        Client client = StompTestClient.connect(port, user.bearer());
        sessions.add(client.session());
        return client;
    }
}
