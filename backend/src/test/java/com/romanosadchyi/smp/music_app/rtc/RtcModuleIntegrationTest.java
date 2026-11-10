package com.romanosadchyi.smp.music_app.rtc;

import com.jayway.jsonpath.JsonPath;
import com.romanosadchyi.smp.music_app.IntegrationTest;
import com.romanosadchyi.smp.music_app.RoomTestFixtures;
import com.romanosadchyi.smp.music_app.RoomTestFixtures.Registered;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The ICE servers endpoint through the real HTTP layer (KAN-46), with the TURN settings of
 * {@code application-test.yaml}.
 */
@IntegrationTest
class RtcModuleIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void iceServers_ForASignedInUser_ReturnsStunAndTurnWithACredentialCoturnAccepts() throws Exception {
        Registered user = new RoomTestFixtures(mockMvc, jdbcTemplate).register();
        long before = Instant.now().getEpochSecond();

        String body = mockMvc.perform(get("/api/rtc/ice-servers").header(HttpHeaders.AUTHORIZATION, user.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.iceServers.length()").value(2))
                .andExpect(jsonPath("$.iceServers[0].urls[0]").value("stun:turn.test:3478"))
                .andExpect(jsonPath("$.iceServers[0].username").doesNotExist())
                .andExpect(jsonPath("$.iceServers[0].credential").doesNotExist())
                .andExpect(jsonPath("$.iceServers[1].urls[0]").value("turn:turn.test:3478?transport=udp"))
                .andExpect(jsonPath("$.iceServers[1].urls[1]").value("turns:turn.test:443?transport=tcp"))
                .andReturn().getResponse().getContentAsString();

        String username = JsonPath.read(body, "$.iceServers[1].username");
        String[] parts = username.split(":");
        assertThat(parts).hasSize(2);
        assertThat(parts[1]).isEqualTo(user.id().toString());
        // The default lifetime is one hour
        assertThat(Long.parseLong(parts[0])).isBetween(before + 3600, Instant.now().getEpochSecond() + 3600);
        assertThat(Instant.parse(JsonPath.<String>read(body, "$.expiresAt")).getEpochSecond())
                .isEqualTo(Long.parseLong(parts[0]));
        assertThat(JsonPath.<String>read(body, "$.iceServers[1].credential"))
                .isEqualTo(sign("test-turn-secret", username));
        assertThat(body).doesNotContain("test-turn-secret");
    }

    @Test
    void iceServers_WithoutAToken_IsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/rtc/ice-servers"))
                .andExpect(status().isUnauthorized());
    }

    private static String sign(String secret, String username) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        return Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
    }
}
