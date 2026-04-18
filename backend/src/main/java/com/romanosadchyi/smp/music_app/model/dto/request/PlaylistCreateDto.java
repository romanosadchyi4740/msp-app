package com.romanosadchyi.smp.music_app.model.dto.request;

public record PlaylistCreateDto(
        String title,
        String description,
        boolean isPublic
) {}
