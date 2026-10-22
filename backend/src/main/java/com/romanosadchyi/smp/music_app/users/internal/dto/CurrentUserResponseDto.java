package com.romanosadchyi.smp.music_app.users.internal.dto;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * The signed-in user's own profile. Unlike the public {@code UserResponseDto} it includes the email address, so it
 * is returned only to the user it describes.
 */
@Builder
public record CurrentUserResponseDto(
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
