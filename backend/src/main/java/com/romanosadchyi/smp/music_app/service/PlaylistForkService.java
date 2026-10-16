package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.service.mapping.PlaylistMappingService;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Forking a playlist into a private local copy and pulling new tracks from its origin.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistForkService {

    PlaylistRepository playlistRepository;
    PlaylistMappingService playlistMappingService;

    @Transactional
    public PlaylistResponseDto forkPlaylist(UUID playlistId, UUID userId) {
        Playlist original = findPlaylist(playlistId);
        checkCanFork(original, userId);

        Playlist forked = playlistRepository.save(buildFork(original, userId));
        return playlistMappingService.map(forked);
    }

    @Transactional
    public PlaylistSyncResponseDto syncOrigin(UUID playlistId, UUID userId) {
        Playlist localPlaylist = findPlaylist(playlistId);
        checkCanSync(localPlaylist, userId);

        List<Track> newTracks = tracksMissingFrom(localPlaylist, localPlaylist.getForkedFrom());
        if (newTracks.isEmpty()) {
            return new PlaylistSyncResponseDto(playlistMappingService.map(localPlaylist), 0);
        }

        localPlaylist.getTracks().addAll(newTracks);
        Playlist synced = playlistRepository.save(localPlaylist);
        return new PlaylistSyncResponseDto(playlistMappingService.map(synced), newTracks.size());
    }

    private Playlist findPlaylist(UUID playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));
    }

    private void checkCanFork(Playlist original, UUID userId) {
        if (!original.isPublic() && !isOwner(original, userId)) {
            throw new UserNotAuthorizedException("You don't have access to fork this playlist");
        }
    }

    private void checkCanSync(Playlist localPlaylist, UUID userId) {
        if (!isOwner(localPlaylist, userId)) {
            throw new UserNotAuthorizedException("You can only sync your own local instances");
        }
        if (localPlaylist.getForkedFrom() == null) {
            throw new IllegalArgumentException("This playlist does not have an origin node");
        }
    }

    private boolean isOwner(Playlist playlist, UUID userId) {
        return playlist.getOwnerId().equals(userId);
    }

    private Playlist buildFork(Playlist original, UUID userId) {
        return Playlist.builder()
                .title(original.getTitle() + " [Local Instance]")
                .description(original.getDescription())
                .coverUrl(original.getCoverUrl())
                .ownerId(userId)
                .isPublic(false)
                .forkedFrom(original)
                .tracks(new ArrayList<>(original.getTracks()))
                .build();
    }

    /**
     * Tracks of {@code origin} that {@code local} does not contain yet, in the origin's order.
     */
    private List<Track> tracksMissingFrom(Playlist local, Playlist origin) {
        Set<UUID> localTrackIds = local.getTracks().stream()
                .map(Track::getId)
                .collect(Collectors.toSet());

        return origin.getTracks().stream()
                .filter(track -> !localTrackIds.contains(track.getId()))
                .toList();
    }
}
