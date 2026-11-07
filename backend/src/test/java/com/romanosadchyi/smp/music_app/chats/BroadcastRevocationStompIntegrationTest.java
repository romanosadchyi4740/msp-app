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
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A member who rejects or declines a group chat stops hearing its listening broadcasts at once, also on a
 * subscription made while they were still a member (KAN-93); the other members keep hearing them.
 */
@IntegrationTest
class BroadcastRevocationStompIntegrationTest {

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
    void rejectGroupChat_MemberStopsHearingItsBroadcast_OthersStillDo() throws Exception {
        Registered master = fixtures.register();
        Registered leaving = fixtures.register();
        Registered staying = fixtures.register();
        UUID chatId = groupChat(master, leaving, staying);

        assertLeavingStopsHearing(chatId, master, leaving, staying,
                put("/api/chats/" + chatId + "/reject").header(HttpHeaders.AUTHORIZATION, leaving.bearer()));
    }

    @Test
    void declineGroupMembership_MemberStopsHearingItsBroadcast_OthersStillDo() throws Exception {
        Registered master = fixtures.register();
        Registered leaving = fixtures.register();
        Registered staying = fixtures.register();
        UUID chatId = groupChat(master, leaving, staying);

        assertLeavingStopsHearing(chatId, master, leaving, staying,
                post("/api/chats/" + chatId + "/membership/" + leaving.id() + "/decline")
                        .header(HttpHeaders.AUTHORIZATION, leaving.bearer()));
    }

    private void assertLeavingStopsHearing(UUID chatId, Registered master, Registered leaving, Registered staying,
                                           RequestBuilder leave) throws Exception {
        String broadcast = chatId + "/" + UUID.randomUUID();
        Client masterClient = connect(master);
        Client leavingClient = connect(leaving);
        Client stayingClient = connect(staying);
        BlockingQueue<Map<String, Object>> leavingHears = StompTestClient.subscribe(leavingClient.session(),
                "/topic/broadcast/" + broadcast);
        BlockingQueue<Map<String, Object>> stayingHears = StompTestClient.subscribe(stayingClient.session(),
                "/topic/broadcast/" + broadcast);
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        // Both members hear the broadcast while they are in the chat
        play(masterClient, broadcast, 1.0);
        assertThat(StompTestClient.poll(leavingHears)).containsEntry("command", "PLAY");
        assertThat(StompTestClient.poll(stayingHears)).containsEntry("command", "PLAY");

        mockMvc.perform(leave).andExpect(status().isOk());

        play(masterClient, broadcast, 2.0);
        assertThat(StompTestClient.poll(stayingHears)).containsEntry("command", "PLAY");
        assertThat(leavingHears.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
        // The connection itself stays open: nothing was refused, the subscription just ended
        assertThat(leavingClient.errors()).isEmpty();
        assertThat(leavingClient.session().isConnected()).isTrue();
    }

    private static void play(Client master, String broadcast, double timestamp) {
        master.session().send("/app/broadcast/" + broadcast + "/sync", Map.of("command", "PLAY", "timestamp",
                timestamp));
    }

    /**
     * A group chat of the master (its admin) and two members who accepted. Chats are written directly, as other
     * integration tests do, so the test does not depend on how groups are created.
     */
    private UUID groupChat(Registered master, Registered... members) {
        UUID chatId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO chats (id, is_group, created_at, updated_at) VALUES (?, TRUE, now(), now())",
                chatId);
        insertMembership(chatId, master, true);
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
