package com.romanosadchyi.smp.music_app.users.internal.dto;

public record UserUpdateDto(
        String displayName,
        String biography,
        String avatarUrl
) {
}
