package com.romanosadchyi.smp.music_app.chats.internal.dto;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ChatMessageRequestDto(
        UUID chatId,
        UUID recipientId,
        String content,
        UUID sharedTrackId,
        Integer sharedTrackTimestamp,
        String fileUrl
) {
}
