package com.romanosadchyi.smp.music_app.model.dto.response;

import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;

import java.time.Instant;
import java.util.UUID;

public record StatusBroadcastResponse(
        UUID id,
        UserResponseDto author,
        String content,
        TrackResponseDto attachedTrack,
        Instant timestamp,
        ConsensusPollResponse poll
) {
}
