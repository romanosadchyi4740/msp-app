package com.romanosadchyi.smp.music_app.model.dto.request;

public record RegisterRequest(
        String email,
        String password,
        String displayName
) {}
