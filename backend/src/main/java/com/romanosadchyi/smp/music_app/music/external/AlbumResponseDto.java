package com.romanosadchyi.smp.music_app.music.external;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AlbumResponseDto(UUID id,
                               String title,
                               String description,
                               String coverUrl,
                               UUID authorId,
                               String authorName,
                               LocalDate releaseDate,
                               List<TrackResponseDto> tracks) {
}
