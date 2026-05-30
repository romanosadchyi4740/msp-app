package com.romanosadchyi.smp.music_app.model.dto.response;

import java.util.List;
import java.util.UUID;

public record ConsensusPollResponse(
        UUID id,
        String question,
        List<PollOptionResponse> options,
        boolean hasVoted,
        UUID votedOptionId
) {
}
