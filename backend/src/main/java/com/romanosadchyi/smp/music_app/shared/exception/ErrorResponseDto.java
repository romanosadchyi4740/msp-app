package com.romanosadchyi.smp.music_app.shared.exception;

public record ErrorResponseDto(
        String code,
        String message
) {
}
