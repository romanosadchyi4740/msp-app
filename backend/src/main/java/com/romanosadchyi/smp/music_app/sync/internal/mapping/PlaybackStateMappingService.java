package com.romanosadchyi.smp.music_app.sync.internal.mapping;

import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import org.springframework.stereotype.Component;

@Component
public class PlaybackStateMappingService {

    /**
     * The state as clients see it at {@code serverTime}; a state not saved yet has version 0.
     */
    public PlaybackStateDto map(PlaybackState state, long serverTime) {
        return new PlaybackStateDto(
                state.getSessionId(),
                state.getMeasure(),
                state.getBeat(),
                state.getTempoBpm(),
                state.getBeatsPerMeasure(),
                state.getBeatUnit(),
                state.getStatus(),
                state.getAnchorTime(),
                state.isMetronomeOn(),
                state.getVersion() != null ? state.getVersion() : 0,
                serverTime);
    }
}
