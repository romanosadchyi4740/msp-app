package com.romanosadchyi.smp.music_app.model.dto.response;

import java.util.UUID;

public record PollOptionResponse(
        UUID id,
        String optionText,
        int voteCount
) {
}
