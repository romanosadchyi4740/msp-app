package com.romanosadchyi.smp.music_app.playlists.internal.dto;

public record PlaylistCreateDto(
        String title,
        String description,
        boolean isPublic
) {
}
