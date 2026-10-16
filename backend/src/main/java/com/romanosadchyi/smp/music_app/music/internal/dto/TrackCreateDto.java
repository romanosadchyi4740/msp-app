package com.romanosadchyi.smp.music_app.music.internal.dto;

import java.util.Set;
import java.util.UUID;

public record TrackCreateDto(
        String title,
        Integer duration,
        String coverUrl,
        UUID albumId,
        Integer trackNumber,
        Set<UUID> genreIds) {
}
