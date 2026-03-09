package com.romanosadchyi.smp.music_app.model.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AlbumResponseDto(UUID id,
                               String title,
                               String description,
                               String coverUrl,
                               String authorName,
                               LocalDate releaseDate,
                               List<TrackResponseDto> tracks) {
}
