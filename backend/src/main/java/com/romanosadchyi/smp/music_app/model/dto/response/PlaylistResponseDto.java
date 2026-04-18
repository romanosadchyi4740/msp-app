package com.romanosadchyi.smp.music_app.model.dto.response;

import java.util.List;
import java.util.UUID;

public record PlaylistResponseDto(
        UUID id,
        String title,
        String description,
        UUID ownerId,
        boolean isPublic,
        String coverUrl,
        List<TrackResponseDto> tracks,
        int tracksCount
) {}
