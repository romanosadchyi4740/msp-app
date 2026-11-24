package com.romanosadchyi.smp.music_app.telemetry.internal.dto;

import java.util.List;

public record ConsensusPollRequest(
        String question,
        List<String> options
) {
}
