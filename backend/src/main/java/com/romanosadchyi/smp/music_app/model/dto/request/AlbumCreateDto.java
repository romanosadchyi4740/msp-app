package com.romanosadchyi.smp.music_app.model.dto.request;

public record AlbumCreateDto(
        String title,
        String description,
        String coverUrl) {
}
