package com.romanosadchyi.smp.music_app.playlists.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.IntegrationRequest;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps integration requests to responses with the requester's profile from {@link UserLookup} and the proposed
 * tracks from {@link TrackLookup}; {@link #mapAll} looks up requesters and tracks once for all requests.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IntegrationMappingService {

    UserLookup userLookup;
    TrackLookup trackLookup;

    public IntegrationRequestResponseDto map(IntegrationRequest request) {
        return mapAll(List.of(request)).getFirst();
    }

    public List<IntegrationRequestResponseDto> mapAll(Collection<IntegrationRequest> requests) {
        Map<UUID, TrackResponseDto> tracks = tracksOf(requests);
        Map<UUID, UserResponseDto> requesters = userLookup.findProfiles(requests.stream()
                .map(IntegrationRequest::getRequesterId)
                .distinct()
                .toList());
        return requests.stream()
                .map(request -> map(request, tracks, requesters))
                .toList();
    }

    private Map<UUID, TrackResponseDto> tracksOf(Collection<IntegrationRequest> requests) {
        List<UUID> trackIds = requests.stream()
                .flatMap(request -> request.getProposedTrackIds().stream())
                .distinct()
                .toList();
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        return trackLookup.findTracks(trackIds).stream()
                .collect(Collectors.toMap(TrackResponseDto::id, Function.identity()));
    }

    private IntegrationRequestResponseDto map(IntegrationRequest request, Map<UUID, TrackResponseDto> tracks,
                                              Map<UUID, UserResponseDto> requesters) {
        return new IntegrationRequestResponseDto(
                request.getId(),
                request.getTargetPlaylist().getId(),
                requesters.get(request.getRequesterId()),
                request.getProposedTrackIds().stream().map(tracks::get).filter(Objects::nonNull).toList(),
                request.getStatus().name(),
                request.getCreatedAt()
        );
    }
}
