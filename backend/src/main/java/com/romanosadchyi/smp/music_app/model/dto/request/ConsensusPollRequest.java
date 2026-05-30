package com.romanosadchyi.smp.music_app.model.dto.request;

import java.util.List;

public record ConsensusPollRequest(
        String question,
        List<String> options
) {
}
