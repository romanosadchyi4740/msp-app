package com.romanosadchyi.smp.music_app.model.dto.response;

import lombok.Builder;

import java.util.UUID;

@Builder(toBuilder = true)
public record TrackResponseDto(UUID id,
                               String title,
                               String authorName,
                               UUID authorId,
                               String albumTitle,
                               Integer duration,
                               String coverUrl) {
}
