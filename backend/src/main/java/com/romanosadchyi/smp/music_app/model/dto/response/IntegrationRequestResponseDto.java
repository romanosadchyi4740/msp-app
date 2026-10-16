package com.romanosadchyi.smp.music_app.model.dto.response;

import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record IntegrationRequestResponseDto(
        UUID id,
        UUID targetPlaylistId,
        UserResponseDto requester,
        List<TrackResponseDto> proposedTracks,
        String status,
        LocalDateTime createdAt
) {
}
