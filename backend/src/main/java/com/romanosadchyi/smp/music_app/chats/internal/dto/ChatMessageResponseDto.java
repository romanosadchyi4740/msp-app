package com.romanosadchyi.smp.music_app.chats.internal.dto;

import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
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
