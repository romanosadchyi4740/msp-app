package com.romanosadchyi.smp.music_app.model.dto.request;

import java.util.UUID;

public record TelemetryBroadcastRequest(
        String content,
        UUID attachedTrackId,
        ConsensusPollRequest poll
) {
}
