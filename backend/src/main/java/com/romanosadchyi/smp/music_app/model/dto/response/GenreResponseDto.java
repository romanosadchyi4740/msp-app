package com.romanosadchyi.smp.music_app.model.dto.response;

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
