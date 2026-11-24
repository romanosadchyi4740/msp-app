package com.romanosadchyi.smp.music_app.rtc.internal.service;

import com.romanosadchyi.smp.music_app.rtc.internal.api.model.IceServer;
import com.romanosadchyi.smp.music_app.rtc.internal.api.model.IceServersResponse;
import com.romanosadchyi.smp.music_app.rtc.internal.config.TurnProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IceServerServiceTest {

    static final Instant NOW = Instant.parse("2026-11-10T10:00:00.750Z");
    static final List<String> TURN_URLS = List.of("turn:turn.example.com:3478?transport=udp",
            "turn:turn.example.com:3478?transport=tcp", "turns:turn.example.com:443?transport=tcp");
    static final List<String> STUN_URLS = List.of("stun:turn.example.com:3478");

    UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Test
    void iceServers_SignsTheExpiryAndUserWithTheSecretAsCoturnDoes() {
        IceServersResponse response = service("north-secret", STUN_URLS, 3600).iceServers(userId);

        IceServer turn = response.getIceServers().get(1);
        assertThat(turn.getUrls()).isEqualTo(TURN_URLS);
        // 2026-11-10T11:00:00Z in epoch seconds, then the user
        assertThat(turn.getUsername()).isEqualTo("1794308400:00000000-0000-0000-0000-000000000001");
        // base64(HMAC-SHA1("north-secret", username)), computed outside this code base
        assertThat(turn.getCredential()).isEqualTo("SBjKdgKjMPn8Q19fUN3O0LLPRbw=");
    }

    @Test
    void iceServers_StunComesFirstWithoutCredentials() {
        IceServersResponse response = service("north-secret", STUN_URLS, 3600).iceServers(userId);

        assertThat(response.getIceServers()).hasSize(2);
        IceServer stun = response.getIceServers().getFirst();
        assertThat(stun.getUrls()).isEqualTo(STUN_URLS);
        assertThat(stun.getUsername()).isNull();
        assertThat(stun.getCredential()).isNull();
    }

    @Test
    void iceServers_NoStunConfigured_ReturnsOnlyTurn() {
        IceServersResponse response = service("north-secret", List.of(), 3600).iceServers(userId);

        assertThat(response.getIceServers()).hasSize(1);
        assertThat(response.getIceServers().getFirst().getCredential()).isNotBlank();
    }

    @Test
    void iceServers_ExpiresAfterTheConfiguredLifetimeInWholeSeconds() {
        IceServersResponse response = service("north-secret", STUN_URLS, 21600).iceServers(userId);

        assertThat(response.getExpiresAt()).isEqualTo(Instant.parse("2026-11-10T16:00:00Z"));
        assertThat(response.getIceServers().get(1).getUsername())
                .startsWith(response.getExpiresAt().getEpochSecond() + ":");
    }

    @Test
    void iceServers_AnotherUserOrSecret_GetsAnotherCredential() {
        String credential = service("north-secret", STUN_URLS, 3600).iceServers(userId)
                .getIceServers().get(1).getCredential();

        assertThat(service("north-secret", STUN_URLS, 3600).iceServers(UUID.randomUUID())
                .getIceServers().get(1).getCredential()).isNotEqualTo(credential);
        assertThat(service("south-secret", STUN_URLS, 3600).iceServers(userId)
                .getIceServers().get(1).getCredential()).isNotEqualTo(credential);
    }

    private static IceServerService service(String secret, List<String> stunUrls, long ttlSeconds) {
        return new IceServerService(new TurnProperties(secret, TURN_URLS, stunUrls, ttlSeconds),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }
}
