package com.romanosadchyi.smp.music_app.model.dto.response;

import lombok.Builder;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;

import java.util.UUID;

@Builder
public record ChatMembershipDto(
        UUID userId,
        String displayName,
        String avatarUrl,
        ChatStatus status,
        boolean isAdmin
) {}
