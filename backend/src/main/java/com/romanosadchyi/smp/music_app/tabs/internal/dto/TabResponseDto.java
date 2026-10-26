package com.romanosadchyi.smp.music_app.tabs.internal.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * A tab as shown to a reader. {@code gpVersion} is set only for Guitar Pro tabs; {@code rawText} only for an ASCII
 * tab read on its own, not in lists.
 */
@Builder
public record TabResponseDto(
        UUID id,
        TabFormat format,
        String title,
        String artist,
        UUID ownerId,
        String ownerName,
        Integer tempoBpm,
        String timeSignature,
        boolean isPublic,
        boolean hidden,
        String gpVersion,
        String rawText,
        Instant createdAt,
        Instant updatedAt) {
}
