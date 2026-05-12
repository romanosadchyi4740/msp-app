package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.model.dto.request.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistSyncResponseDto;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    
    @Transactional
    public PlaylistResponseDto removeTrackFromPlaylist(UUID userId, UUID playlistId, UUID trackId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!playlist.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException("You can only remove tracks from your own playlists");
        }

        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new TrackNotFoundException("Track not found"));
        playlist.getTracks().remove(track);

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
    
    @Transactional(readOnly = true)
    public List<PlaylistResponseDto> getMyPlaylists(UUID userId) {
        return playlistRepository.findAllByOwnerId(userId).stream()
                .map(playlistMappingService::map)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PlaylistResponseDto> getUserPublicPlaylists(UUID userId) {
        return playlistRepository.findAllByOwnerIdAndIsPublicTrue(userId).stream()
                .map(playlistMappingService::map)
                .collect(Collectors.toList());
    }

    @Transactional
    public PlaylistResponseDto forkPlaylist(UUID playlistId, UUID userId) {
        Playlist original = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!original.isPublic() && !original.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException("You don't have access to fork this playlist");
        }

        Playlist forked = Playlist.builder()
                .title(original.getTitle() + " [Local Instance]")
                .description(original.getDescription())
                .coverUrl(original.getCoverUrl())
                .ownerId(userId)
                .isPublic(false)
                .forkedFrom(original)
                .tracks(new java.util.ArrayList<>(original.getTracks()))
                .build();

        return playlistMappingService.map(playlistRepository.save(forked));
    }

    @Transactional
    public PlaylistSyncResponseDto syncOrigin(UUID playlistId, UUID userId) {
        Playlist localPlaylist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!localPlaylist.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException("You can only sync your own local instances");
        }

        if (localPlaylist.getForkedFrom() == null) {
            throw new IllegalArgumentException("This playlist does not have an origin node");
        }

        Playlist originPlaylist = localPlaylist.getForkedFrom();
        
        Set<UUID> localTrackIds = localPlaylist.getTracks().stream()
                .map(Track::getId)
                .collect(Collectors.toSet());

        List<Track> newTracksToAdd = originPlaylist.getTracks().stream()
                .filter(track -> !localTrackIds.contains(track.getId()))
                .toList();

        if (newTracksToAdd.isEmpty()) {
            return new PlaylistSyncResponseDto(playlistMappingService.map(localPlaylist), 0);
        }

        localPlaylist.getTracks().addAll(newTracksToAdd);
        
        return new PlaylistSyncResponseDto(playlistMappingService.map(playlistRepository.save(localPlaylist)), newTracksToAdd.size());
    }
    
    @Transactional
    public PlaylistResponseDto toggleVisibility(UUID playlistId, UUID userId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!playlist.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException("You can only modify your own playlists");
        }

        playlist.setPublic(!playlist.isPublic());

        return playlistMappingService.map(playlistRepository.save(playlist));
    }

    @Transactional
    public void deletePlaylist(UUID playlistId, UUID userId) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException("Playlist not found"));

        if (!playlist.getOwnerId().equals(userId)) {
            throw new UserNotAuthorizedException("Access Denied: You do not own this local instance.");
        }

        log.info("Dismantling Instance: {} (ID: {})", playlist.getTitle(), playlistId);

        // 1. Fork Lineage Decoupling (Forks survive their origin)
        playlistRepository.detachParentPlaylist(playlist);

        // 2. Delete Playlist (ManyToMany relations in playlist_tracks will be cleared automatically by JPA)
        playlistRepository.delete(playlist);
    }

}
