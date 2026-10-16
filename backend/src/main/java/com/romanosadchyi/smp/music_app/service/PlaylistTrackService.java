package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.PlaylistMappingService;
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
    TrackRepository trackRepository;
    PlaylistMappingService playlistMappingService;

    @Transactional
    public PlaylistResponseDto addTrackToPlaylist(UUID userId, UUID playlistId, UUID trackId) {
        Playlist playlist = findOwnedPlaylist(playlistId, userId,
                "You can only add tracks to your own playlists");
        Track track = findTrack(trackId);

        playlist.getTracks().add(track);
        useTrackCoverIfMissing(playlist, track);

        return saveAndMap(playlist);
    }

    @Transactional
    public PlaylistResponseDto removeTrackFromPlaylist(UUID userId, UUID playlistId, UUID trackId) {
        Playlist playlist = findOwnedPlaylist(playlistId, userId,
                "You can only remove tracks from your own playlists");
        Track track = findTrack(trackId);

        playlist.getTracks().remove(track);

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

    private Track findTrack(UUID trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));
    }

    private void useTrackCoverIfMissing(Playlist playlist, Track track) {
        if (playlist.getCoverUrl() == null && track.getCoverUrl() != null) {
            playlist.setCoverUrl(track.getCoverUrl());
        }
    }

    private PlaylistResponseDto saveAndMap(Playlist playlist) {
        return playlistMappingService.map(playlistRepository.save(playlist));
    }
}
