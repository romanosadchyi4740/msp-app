package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.service.PlaylistService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistController {

    PlaylistService playlistService;

    @PostMapping
    public ResponseEntity<PlaylistResponseDto> createPlaylist(
            @AuthenticationPrincipal User user,
            @RequestBody PlaylistCreateDto dto
    ) {
        log.info("Creating playlist for user: {}", user.getId());
        return ResponseEntity.ok(playlistService.createPlaylist(user.getId(), dto));
    }

    @GetMapping("/me")
    public ResponseEntity<List<PlaylistResponseDto>> getMyPlaylists(@AuthenticationPrincipal User user) {
        log.info("Getting playlists for user: {}", user.getId());
        return ResponseEntity.ok(playlistService.getMyPlaylists(user.getId()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PlaylistResponseDto>> getUserPublicPlaylists(@PathVariable UUID userId) {
        log.info("Getting public playlists for user: {}", userId);
        return ResponseEntity.ok(playlistService.getUserPublicPlaylists(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaylistResponseDto> getPlaylist(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        log.info("Getting playlist with ID: {}", id);
        UUID userId = (user != null) ? user.getId() : null;
        return ResponseEntity.ok(playlistService.getPlaylistById(id, userId));
    }

    @PostMapping("/{id}/fork")
    public ResponseEntity<PlaylistResponseDto> forkPlaylist(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        log.info("Forking playlist {} for user: {}", id, user.getId());
        return ResponseEntity.ok(playlistService.forkPlaylist(id, user.getId()));
    }
    
    @PostMapping("/{id}/sync-origin")
    public ResponseEntity<PlaylistSyncResponseDto> syncOrigin(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        log.info("Syncing playlist {} with origin for user: {}", id, user.getId());
        return ResponseEntity.ok(playlistService.syncOrigin(id, user.getId()));
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<PlaylistResponseDto> toggleVisibility(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user
    ) {
        log.info("Toggling visibility for playlist: {}", id);
        return ResponseEntity.ok(playlistService.toggleVisibility(id, user.getId()));
    }

    @PostMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<PlaylistResponseDto> addTrack(
            @AuthenticationPrincipal User user,
            @PathVariable UUID playlistId,
            @PathVariable UUID trackId
    ) {
        log.info("Adding track {} to playlist: {}", trackId, playlistId);
        return ResponseEntity.ok(playlistService.addTrackToPlaylist(user.getId(), playlistId, trackId));
    }
    
    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<PlaylistResponseDto> removeTrack(
            @AuthenticationPrincipal User user,
            @PathVariable UUID playlistId,
            @PathVariable UUID trackId
    ) {
        log.info("Removing track {} from playlist: {}", trackId, playlistId);
        return ResponseEntity.ok(playlistService.removeTrackFromPlaylist(user.getId(), playlistId, trackId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlaylist(@AuthenticationPrincipal User user, @PathVariable UUID id) {
        log.info("User {} requested dismantling of instance: {}", user.getId(), id);
        playlistService.deletePlaylist(id, user.getId());
        return ResponseEntity.noContent().build();
    }

}
