package com.romanosadchyi.smp.music_app.model.dto.request;

import lombok.Builder;

import java.util.UUID;

@Builder
public record ChatMessageRequest(
        UUID chatId,
        UUID recipientId,
        String content,
        UUID sharedTrackId
) {}
