package com.romanosadchyi.smp.music_app.sync.internal.mapping;

import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PlaybackStateMappingServiceTest {

    PlaybackStateMappingService mappingService = new PlaybackStateMappingService();

    UUID sessionId = UUID.randomUUID();

    @Test
    void map_CopiesEveryFieldAndTheServerTime() {
        PlaybackState state = state();
        state.setVersion(12L);

        PlaybackStateDto result = mappingService.map(state, 5000L);

        assertThat(result).isEqualTo(new PlaybackStateDto(sessionId, 7, 2.5, 96, 3, 4, PlaybackStatus.PLAYING, 4000L,
                true, 12L, 5000L));
    }

    @Test
    void map_UnsavedState_HasVersionZero() {
        assertThat(mappingService.map(state(), 5000L).version()).isZero();
    }

    private PlaybackState state() {
        return PlaybackState.builder()
                .sessionId(sessionId)
                .measure(7)
                .beat(2.5)
                .tempoBpm(96)
                .beatsPerMeasure(3)
                .beatUnit(4)
                .status(PlaybackStatus.PLAYING)
                .anchorTime(4000L)
                .metronomeOn(true)
                .build();
    }
}
