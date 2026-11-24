package com.romanosadchyi.smp.music_app.playlists.internal.mapping;

import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
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
 * Maps playlists to responses. Tracks come from {@link TrackLookup} and the origin owner's name from
 * {@link UserLookup}; {@link #mapAll} looks both up once for all playlists instead of once per playlist.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistMappingService {

    static final String UNKNOWN_OWNER = "Unknown Identity";

    TrackLookup trackLookup;
    UserLookup userLookup;

    public PlaylistResponseDto map(Playlist playlist) {
        return mapAll(List.of(playlist)).getFirst();
    }

    public List<PlaylistResponseDto> mapAll(Collection<Playlist> playlists) {
        Map<UUID, TrackResponseDto> tracks = tracksOf(playlists);
        Map<UUID, UserSummary> originOwners = originOwnersOf(playlists);
        return playlists.stream()
                .map(playlist -> map(playlist, tracks, originOwners))
                .toList();
    }

    private Map<UUID, TrackResponseDto> tracksOf(Collection<Playlist> playlists) {
        List<UUID> trackIds = playlists.stream()
                .flatMap(playlist -> playlist.getTrackIds().stream())
                .distinct()
                .toList();
        if (trackIds.isEmpty()) {
            return Map.of();
        }
        return trackLookup.findTracks(trackIds).stream()
                .collect(Collectors.toMap(TrackResponseDto::id, Function.identity()));
    }

    private Map<UUID, UserSummary> originOwnersOf(Collection<Playlist> playlists) {
        List<UUID> ownerIds = playlists.stream()
                .map(Playlist::getForkedFrom)
                .filter(Objects::nonNull)
                .map(Playlist::getOwnerId)
                .distinct()
                .toList();
        if (ownerIds.isEmpty()) {
            return Map.of();
        }
        return userLookup.findSummaries(ownerIds);
    }

    private static PlaylistResponseDto map(Playlist playlist, Map<UUID, TrackResponseDto> tracks,
                                           Map<UUID, UserSummary> originOwners) {
        List<TrackResponseDto> tracksDto = playlist.getTrackIds().stream()
                .map(tracks::get)
                .filter(Objects::nonNull)
                .toList();

        Playlist origin = playlist.getForkedFrom();
        UUID forkedFromId = null;
        String forkedFromName = null;
        String forkedFromOwnerName = null;

        if (origin != null) {
            forkedFromId = origin.getId();
            forkedFromName = origin.getTitle();
            UserSummary owner = originOwners.get(origin.getOwnerId());
            forkedFromOwnerName = owner != null ? owner.displayName() : UNKNOWN_OWNER;
        }

        return new PlaylistResponseDto(
                playlist.getId(),
                playlist.getTitle(),
                playlist.getDescription(),
                playlist.getOwnerId(),
                playlist.isPublic(),
                playlist.getCoverUrl(),
                tracksDto,
                tracksDto.size(),
                forkedFromId,
                forkedFromName,
                forkedFromOwnerName
        );
    }
}
