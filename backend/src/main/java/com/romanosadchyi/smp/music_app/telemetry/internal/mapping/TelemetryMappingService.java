package com.romanosadchyi.smp.music_app.telemetry.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.telemetry.internal.dto.ConsensusPollResponse;
import com.romanosadchyi.smp.music_app.telemetry.internal.dto.PollOptionResponse;
import com.romanosadchyi.smp.music_app.telemetry.internal.dto.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.ConsensusPoll;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.PollOption;
import com.romanosadchyi.smp.music_app.telemetry.internal.entity.StatusBroadcast;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps status broadcasts and their polls to responses for one viewer, whose own vote is marked in the poll. Attached
 * tracks come from {@link TrackLookup}; {@link #mapPage} looks them up once for the whole page.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TelemetryMappingService {

    UserLookup userLookup;
    TrackLookup trackLookup;

    public StatusBroadcastResponse map(StatusBroadcast broadcast, UUID viewerId) {
        return map(broadcast, viewerId, attachedTracksOf(List.of(broadcast)));
    }

    public Page<StatusBroadcastResponse> mapPage(Page<StatusBroadcast> broadcasts, UUID viewerId) {
        Map<UUID, TrackResponseDto> tracks = attachedTracksOf(broadcasts.getContent());
        return broadcasts.map(broadcast -> map(broadcast, viewerId, tracks));
    }

    private Map<UUID, TrackResponseDto> attachedTracksOf(Collection<StatusBroadcast> broadcasts) {
        List<UUID> trackIds = broadcasts.stream()
                .map(StatusBroadcast::getAttachedTrackId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        return trackLookup.findTracks(trackIds).stream()
                .collect(Collectors.toMap(TrackResponseDto::id, Function.identity()));
    }

    private StatusBroadcastResponse map(StatusBroadcast broadcast, UUID viewerId, Map<UUID, TrackResponseDto> tracks) {
        return new StatusBroadcastResponse(
                broadcast.getId(),
                userLookup.getProfile(broadcast.getAuthorId()),
                broadcast.getContent(),
                broadcast.getAttachedTrackId() != null ? tracks.get(broadcast.getAttachedTrackId()) : null,
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
