package com.romanosadchyi.smp.music_app.tabs.internal.dto;

public record AsciiTabCreateDto(
        String title,
        String artist,
        Integer tempoBpm,
        String timeSignature,
        Boolean isPublic,
        Boolean rightsConfirmed,
        String rawText) {
}
