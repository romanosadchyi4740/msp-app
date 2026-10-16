package com.romanosadchyi.smp.music_app.model.dto.request;

public record UserUpdateDto(
        String displayName,
        String biography,
        String avatarUrl
) {
}
