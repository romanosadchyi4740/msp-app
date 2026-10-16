package com.romanosadchyi.smp.music_app.telemetry.internal.dto;

import java.util.UUID;

public record PollOptionResponse(
        UUID id,
        String optionText,
        int voteCount
) {
}
