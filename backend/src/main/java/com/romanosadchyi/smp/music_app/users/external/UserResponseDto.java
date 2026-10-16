package com.romanosadchyi.smp.music_app.users.external;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserResponseDto(
        UUID id,
        String email,
        String displayName,
        String avatarUrl,
        String biography,
        String role,
        LocalDateTime createdAt,
        Integer followersCount,
        Integer followingCount
) {
}
