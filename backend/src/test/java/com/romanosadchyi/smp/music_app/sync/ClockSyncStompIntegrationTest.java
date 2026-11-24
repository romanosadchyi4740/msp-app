package com.romanosadchyi.smp.music_app.sync;

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
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The clock sync endpoint over a real STOMP connection (KAN-41): the server answers with its clock, to the asking
 * session only.
 */
@IntegrationTest
class ClockSyncStompIntegrationTest {

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
    void time_AnswersTheAskingSessionOnlyWithTheServerClock() throws Exception {
        Registered user = fixtures.register();
        Client asking = connect(user.bearer());
        Client otherTab = connect(user.bearer());
        BlockingQueue<Map<String, Object>> answers = StompTestClient.subscribe(asking.session(), "/user/queue/time");
        BlockingQueue<Map<String, Object>> otherTabAnswers = StompTestClient.subscribe(otherTab.session(),
                "/user/queue/time");
        Thread.sleep(SUBSCRIBE_SETTLE_MS);

        long before = System.currentTimeMillis();
        asking.session().send("/app/time", Map.of("clientTime", 1_234L));
        Map<String, Object> answer = StompTestClient.poll(answers);
        long after = System.currentTimeMillis();

        assertThat(((Number) answer.get("clientTime")).longValue()).isEqualTo(1_234L);
        assertThat(((Number) answer.get("serverTime")).longValue()).isBetween(before, after);
        assertThat(otherTabAnswers.poll(SUBSCRIBE_SETTLE_MS, TimeUnit.MILLISECONDS)).isNull();
        assertThat(asking.errors()).isEmpty();
    }

    private Client connect(String bearer) throws Exception {
        Client client = StompTestClient.connect(port, bearer);
        sessions.add(client.session());
        return client;
    }
}
