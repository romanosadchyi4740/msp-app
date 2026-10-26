package com.romanosadchyi.smp.music_app.tabs.internal.dto;

/**
 * The form fields sent with a Guitar Pro file.
 */
public record GuitarProTabUploadDto(
        String title,
        String artist,
        Integer tempoBpm,
        String timeSignature,
        Boolean isPublic,
        Boolean rightsConfirmed) {
}
