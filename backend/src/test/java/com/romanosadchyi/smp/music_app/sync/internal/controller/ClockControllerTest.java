package com.romanosadchyi.smp.music_app.sync.internal.controller;

import com.romanosadchyi.smp.music_app.sync.internal.dto.TimeSyncRequest;
import com.romanosadchyi.smp.music_app.sync.internal.dto.TimeSyncResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ClockControllerTest {

    static final Instant NOW = Instant.parse("2026-11-07T10:00:00.123Z");

    ClockController controller = new ClockController(Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void time_EchoesTheClientTimeWithTheServerTime() {
        TimeSyncResponse response = controller.time(new TimeSyncRequest(1_000L));

        assertThat(response.clientTime()).isEqualTo(1_000L);
        assertThat(response.serverTime()).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void time_WithoutClientTime_StillAnswersWithTheServerTime() {
        TimeSyncResponse response = controller.time(new TimeSyncRequest(null));

        assertThat(response.clientTime()).isNull();
        assertThat(response.serverTime()).isEqualTo(NOW.toEpochMilli());
    }
}
