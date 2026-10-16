package com.romanosadchyi.smp.music_app.users.internal.dto;

public record LoginRequestDto(
        String email,
        String password
) {
}
