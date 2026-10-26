package com.romanosadchyi.smp.music_app.tabs.internal.dto;

/**
 * Replaces a tab's metadata. {@code rawText} is required for an ASCII tab and must be absent for a Guitar Pro tab,
 * whose file cannot be replaced.
 */
public record TabUpdateDto(
        String title,
        String artist,
        Integer tempoBpm,
        String timeSignature,
        Boolean isPublic,
        String rawText) {
}
