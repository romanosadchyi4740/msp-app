package com.romanosadchyi.smp.music_app.users.internal.dto;

public record RegisterRequestDto(
        String email,
        String password,
        String displayName
) {
}
