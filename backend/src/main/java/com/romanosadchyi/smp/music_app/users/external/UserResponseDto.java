package com.romanosadchyi.smp.music_app.users.external;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The public profile, shown to other users. It has no email address; the user's own profile with the email is
 * returned only by {@code /api/users/me}.
 */
@Builder
public record UserResponseDto(
        UUID id,
        String displayName,
        String avatarUrl,
        String biography,
        String role,
        LocalDateTime createdAt,
        Integer followersCount,
        Integer followingCount
) {
}
