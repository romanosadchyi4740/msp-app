package com.romanosadchyi.smp.music_app.model.dto.request;

public record RegisterRequestDto(
        String email,
        String password,
        String displayName
) {}
