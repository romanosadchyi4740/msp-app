package com.romanosadchyi.smp.music_app.telemetry.internal.dto;

import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
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
