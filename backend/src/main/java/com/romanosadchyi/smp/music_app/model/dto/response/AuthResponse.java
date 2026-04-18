package com.romanosadchyi.smp.music_app.model.dto.response;

public record AuthResponse(
        String accessToken,
        String displayName,
        String userId,
        String role
) {}
