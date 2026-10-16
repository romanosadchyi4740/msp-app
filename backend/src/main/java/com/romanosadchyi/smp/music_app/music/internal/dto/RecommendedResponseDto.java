package com.romanosadchyi.smp.music_app.music.internal.dto;

import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import lombok.Builder;

import java.util.List;

@Builder
public record RecommendedResponseDto(
        String genreName,
        List<TrackResponseDto> tracks
) {
}
