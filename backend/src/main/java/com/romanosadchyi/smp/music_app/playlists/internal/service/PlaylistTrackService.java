package com.romanosadchyi.smp.music_app.playlists.internal.service;

import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.playlists.internal.dto.PlaylistResponseDto;
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

import java.util.UUID;

/**
 * Adding tracks to and removing them from a playlist the user owns.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistTrackService {

    PlaylistRepository playlistRepository;
    TrackLookup trackLookup;
    PlaylistMappingService playlistMappingService;

    @Transactional
    public PlaylistResponseDto addTrackToPlaylist(UUID userId, UUID playlistId, UUID trackId) {
        Playlist playlist = findOwnedPlaylist(playlistId, userId,
                "You can only add tracks to your own playlists");
        TrackResponseDto track = trackLookup.getTrack(trackId);

        playlist.getTrackIds().add(trackId);
        useTrackCoverIfMissing(playlist, track);

        return saveAndMap(playlist);
    }

    @Transactional
    public PlaylistResponseDto removeTrackFromPlaylist(UUID userId, UUID playlistId, UUID trackId) {
        Playlist playlist = findOwnedPlaylist(playlistId, userId,
                "You can only remove tracks from your own playlists");
        if (!trackLookup.exists(trackId)) {
            throw new TrackNotFoundException("Track not found");
        }

        playlist.getTrackIds().remove(trackId);

        return saveAndMap(playlist);
    }

    private Playlist findOwnedPlaylist(UUID playlistId, UUID userId, String notOwnerMessage) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));
        if (!playlist.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException(notOwnerMessage);
        }
        return playlist;
    }

    /**
     * Gives a playlist without a cover the track's response cover (the track's own, else its album's).
     */
    private void useTrackCoverIfMissing(Playlist playlist, TrackResponseDto track) {
        if (playlist.getCoverUrl() == null && track.coverUrl() != null) {
            playlist.setCoverUrl(track.coverUrl());
        }
    }

    private PlaylistResponseDto saveAndMap(Playlist playlist) {
        return playlistMappingService.map(playlistRepository.save(playlist));
    }
}
