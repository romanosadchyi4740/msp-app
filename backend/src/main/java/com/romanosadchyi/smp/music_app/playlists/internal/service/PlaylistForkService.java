package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.entity.Playlist;
import com.romanosadchyi.smp.music_app.playlists.internal.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.playlists.internal.mapping.PlaylistMappingService;
import com.romanosadchyi.smp.music_app.playlists.internal.repository.PlaylistRepository;
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

        List<UUID> newTracks = tracksMissingFrom(localPlaylist, localPlaylist.getForkedFrom());
        if (newTracks.isEmpty()) {
            return new PlaylistSyncResponseDto(playlistMappingService.map(localPlaylist), 0);
        }

        localPlaylist.getTrackIds().addAll(newTracks);
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
                .trackIds(new ArrayList<>(original.getTrackIds()))
                .build();
    }

    /**
     * Tracks of {@code origin} that {@code local} does not contain yet, in the origin's order.
     */
    private List<UUID> tracksMissingFrom(Playlist local, Playlist origin) {
        Set<UUID> localTrackIds = Set.copyOf(local.getTrackIds());

        return origin.getTrackIds().stream()
                .filter(trackId -> !localTrackIds.contains(trackId))
                .toList();
    }
}
