package com.romanosadchyi.smp.music_app.music.external;

import lombok.Builder;

import java.util.UUID;

@Builder
public record GenreResponseDto(
        UUID id,
        String name,
        String slug,
        String colorHex
) {
}
