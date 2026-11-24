package com.romanosadchyi.smp.music_app.music.external;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder(toBuilder = true)
public record TrackResponseDto(UUID id,
                               String title,
                               String authorName,
                               UUID authorId,
                               String albumTitle,
                               Integer duration,
                               String coverUrl,
                               List<GenreResponseDto> genres) {
}
