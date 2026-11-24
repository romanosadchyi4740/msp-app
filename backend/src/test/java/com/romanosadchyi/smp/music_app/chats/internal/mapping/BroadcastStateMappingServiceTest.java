package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStateDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStatus;
import com.romanosadchyi.smp.music_app.chats.internal.service.BroadcastState;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BroadcastStateMappingServiceTest {

    static final long NOW = 1_800_000_000_000L;

    BroadcastStateMappingService mappingService =
            new BroadcastStateMappingService(Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));

    @Test
    void map_CopiesTheStateAndStampsTheServerTime() {
        BroadcastState state = BroadcastState.builder()
                .broadcastId(UUID.randomUUID())
                .chatId(UUID.randomUUID())
                .hostId(UUID.randomUUID())
                .hostSessionId("secret-session")
                .hostSequence(42)
                .status(BroadcastStatus.ACTIVE)
                .trackId(UUID.randomUUID())
                .position(61.5)
                .playing(true)
                .anchorTime(NOW - 2_000)
                .version(7)
                .build();

        BroadcastStateDto dto = mappingService.map(state);

        assertThat(dto).isEqualTo(new BroadcastStateDto(state.broadcastId(), state.chatId(), state.hostId(),
                BroadcastStatus.ACTIVE, state.trackId(), 61.5, true, NOW - 2_000, NOW, 7));
    }

    @Test
    void unknown_IsAnEndedBroadcastWithoutHostOrTrack() {
        UUID broadcastId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        BroadcastStateDto dto = mappingService.unknown(broadcastId, chatId);

        assertThat(dto.status()).isEqualTo(BroadcastStatus.TERMINATED);
        assertThat(dto.broadcastId()).isEqualTo(broadcastId);
        assertThat(dto.chatId()).isEqualTo(chatId);
        assertThat(dto.hostId()).isNull();
        assertThat(dto.trackId()).isNull();
        assertThat(dto.playing()).isFalse();
        assertThat(dto.serverTime()).isEqualTo(NOW);
    }
}
