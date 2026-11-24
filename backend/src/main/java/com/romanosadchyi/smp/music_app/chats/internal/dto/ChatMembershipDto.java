package com.romanosadchyi.smp.music_app.chats.internal.dto;

import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ChatMembershipDto(
        UUID userId,
        String displayName,
        String avatarUrl,
        ChatStatus status,
        boolean isAdmin
) {
}
