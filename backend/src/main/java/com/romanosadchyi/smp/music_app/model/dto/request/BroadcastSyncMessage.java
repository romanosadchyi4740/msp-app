package com.romanosadchyi.smp.music_app.model.dto.request;

import com.romanosadchyi.smp.music_app.model.enums.BroadcastCommand;
import lombok.Builder;

import java.util.UUID;

@Builder
public record BroadcastSyncMessage(
        BroadcastCommand command,
        UUID trackId,
        Double timestamp
) {}
