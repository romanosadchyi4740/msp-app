package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.repository.PlaylistRepository;
import com.romanosadchyi.smp.music_app.service.mapping.PlaylistMappingService;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Playlists a user owns: creating, reading (respecting visibility), toggling visibility and deleting.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistService {

    PlaylistRepository playlistRepository;
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
