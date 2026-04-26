package com.romanosadchyi.smp.music_app.model.dto.response;

import lombok.Builder;
import java.util.List;

@Builder
public record RecommendedResponseDto(
        String genreName,
        List<TrackResponseDto> tracks
) {}
