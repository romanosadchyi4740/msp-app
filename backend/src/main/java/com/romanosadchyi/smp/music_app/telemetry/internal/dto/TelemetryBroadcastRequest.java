package com.romanosadchyi.smp.music_app.telemetry.internal.dto;

import java.util.UUID;

public record TelemetryBroadcastRequest(
        String content,
        UUID attachedTrackId,
        ConsensusPollRequest poll
) {
}
