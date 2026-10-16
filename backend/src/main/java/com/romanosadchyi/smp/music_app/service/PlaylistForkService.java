package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.PlaylistNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Playlist;
import com.romanosadchyi.smp.music_app.model.entity.Track;
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
                .tracks(new ArrayList<>(original.getTracks()))
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
}
