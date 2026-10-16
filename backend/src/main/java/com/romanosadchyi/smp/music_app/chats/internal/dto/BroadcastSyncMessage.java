package com.romanosadchyi.smp.music_app.chats.internal.dto;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastCommand;
import lombok.Builder;

import java.util.UUID;

@Builder
public record BroadcastSyncMessage(
        BroadcastCommand command,
        UUID trackId,
        Double timestamp
) {
}
