package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.service.mapping.PlaylistMappingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistService {

    PlaylistRepository playlistRepository;
    TrackRepository trackRepository;
    PlaylistMappingService playlistMappingService;

    @Transactional
    public PlaylistResponseDto createPlaylist(UUID userId, PlaylistCreateDto dto) {
        Playlist playlist = Playlist.builder()
                .title(dto.title())
                .description(dto.description())
                .ownerId(userId)
                .isPublic(dto.isPublic())
                .build();

        return playlistMappingService.map(playlistRepository.save(playlist));
    }

    @Transactional
    public PlaylistResponseDto addTrackToPlaylist(UUID userId, UUID playlistId, UUID trackId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!playlist.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException("You can only add tracks to your own playlists");
        }

        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));
        playlist.getTracks().add(track);

        if (playlist.getCoverUrl() == null && track.getCoverUrl() != null) {
            playlist.setCoverUrl(track.getCoverUrl());
        }

        return playlistMappingService.map(playlistRepository.save(playlist));
    }

    @Transactional(readOnly = true)
    public PlaylistResponseDto getPlaylistById(UUID playlistId, UUID userId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!playlist.isPublic() && (userId == null || !playlist.getOwnerId().equals(userId))) {
            throw new UserNotAuthorizedException("You don't have access to this private playlist");
        }

        return playlistMappingService.map(playlist);
    }

}
