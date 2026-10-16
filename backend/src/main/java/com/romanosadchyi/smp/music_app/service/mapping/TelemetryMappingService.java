package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ConsensusPollResponse;
import com.romanosadchyi.smp.music_app.model.dto.response.PollOptionResponse;
import com.romanosadchyi.smp.music_app.model.dto.response.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.model.entity.ConsensusPoll;
import com.romanosadchyi.smp.music_app.model.entity.PollOption;
import com.romanosadchyi.smp.music_app.model.entity.StatusBroadcast;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Maps status broadcasts and their polls to responses for one viewer, whose own vote is marked in the poll.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TelemetryMappingService {

    UserLookup userLookup;
    TrackMappingService trackMappingService;

    public StatusBroadcastResponse map(StatusBroadcast broadcast, UUID viewerId) {
        return new StatusBroadcastResponse(
                broadcast.getId(),
                userLookup.getProfile(broadcast.getAuthorId()),
                broadcast.getContent(),
                broadcast.getAttachedTrack() != null ? trackMappingService.map(broadcast.getAttachedTrack()) : null,
                broadcast.getTimestamp(),
                broadcast.getPoll() != null ? map(broadcast.getPoll(), viewerId) : null
        );
    }

    public ConsensusPollResponse map(ConsensusPoll poll, UUID viewerId) {
        List<PollOptionResponse> options = poll.getOptions().stream()
                .map(opt -> new PollOptionResponse(opt.getId(), opt.getOptionText(), opt.getVotes() != null ? opt.getVotes().size() : 0))
                .toList();

        UUID votedOptionId = poll.getOptions().stream()
                .filter(opt -> opt.getVotes() != null
                        && opt.getVotes().stream().anyMatch(v -> v.getVoterId().equals(viewerId)))
                .map(PollOption::getId)
                .findFirst()
                .orElse(null);

        return new ConsensusPollResponse(
                poll.getId(),
                poll.getQuestion(),
                options,
                votedOptionId != null,
                votedOptionId
        );
    }
}
