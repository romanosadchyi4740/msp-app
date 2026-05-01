package com.romanosadchyi.smp.music_app.model.dto.response;

import lombok.Builder;
import java.util.List;
import java.util.UUID;

@Builder
public record ChatResponseDto(
        UUID id,
        boolean isGroup,
        String name,
        List<UserResponseDto> participants,
        ChatMessageResponseDto lastMessage,
        UUID initiatorId,
        String status
) {}
