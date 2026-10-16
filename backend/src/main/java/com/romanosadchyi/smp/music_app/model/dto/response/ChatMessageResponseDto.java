package com.romanosadchyi.smp.music_app.model.dto.response;

import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record ChatMessageResponseDto(
        UUID id,
        UUID chatId,
        UserResponseDto sender,
        String content,
        LocalDateTime timestamp,
        TrackResponseDto sharedTrack,
        Integer sharedTrackTimestamp,
        String fileUrl
) {
}
