package com.romanosadchyi.smp.music_app.users.internal.dto;

public record AuthResponseDto(
        String accessToken,
        String displayName,
        String userId,
        String role
) {
}
