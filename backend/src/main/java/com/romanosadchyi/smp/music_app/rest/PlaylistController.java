package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.IntegrationRequestResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistSyncResponseDto;
import com.romanosadchyi.smp.music_app.service.PlaylistService;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
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
            @AuthenticationPrincipal AuthenticatedUser user,
            @RequestBody PlaylistCreateDto dto
    ) {
        log.info("Creating playlist for user: {}", user.id());
        return ResponseEntity.ok(playlistService.createPlaylist(user.id(), dto));
    }

    @GetMapping("/me")
    public ResponseEntity<List<PlaylistResponseDto>> getMyPlaylists(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting playlists for user: {}", user.id());
        return ResponseEntity.ok(playlistService.getMyPlaylists(user.id()));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PlaylistResponseDto>> getUserPublicPlaylists(@PathVariable UUID userId) {
        log.info("Getting public playlists for user: {}", userId);
        return ResponseEntity.ok(playlistService.getUserPublicPlaylists(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaylistResponseDto> getPlaylist(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        log.info("Getting playlist with ID: {}", id);
        UUID userId = (user != null) ? user.id() : null;
        return ResponseEntity.ok(playlistService.getPlaylistById(id, userId));
    }

    @PostMapping("/{id}/fork")
    public ResponseEntity<PlaylistResponseDto> forkPlaylist(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        log.info("Forking playlist {} for user: {}", id, user.id());
        return ResponseEntity.ok(playlistService.forkPlaylist(id, user.id()));
    }
    
    @PostMapping("/{id}/sync-origin")
    public ResponseEntity<PlaylistSyncResponseDto> syncOrigin(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        log.info("Syncing playlist {} with origin for user: {}", id, user.id());
        return ResponseEntity.ok(playlistService.syncOrigin(id, user.id()));
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<PlaylistResponseDto> toggleVisibility(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        log.info("Toggling visibility for playlist: {}", id);
        return ResponseEntity.ok(playlistService.toggleVisibility(id, user.id()));
    }

    @PostMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<PlaylistResponseDto> addTrack(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID playlistId,
            @PathVariable UUID trackId
    ) {
        log.info("Adding track {} to playlist: {}", trackId, playlistId);
        return ResponseEntity.ok(playlistService.addTrackToPlaylist(user.id(), playlistId, trackId));
    }
    
    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<PlaylistResponseDto> removeTrack(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID playlistId,
            @PathVariable UUID trackId
    ) {
        log.info("Removing track {} from playlist: {}", trackId, playlistId);
        return ResponseEntity.ok(playlistService.removeTrackFromPlaylist(user.id(), playlistId, trackId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlaylist(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        log.info("User {} requested dismantling of instance: {}", user.id(), id);
        playlistService.deletePlaylist(id, user.id());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/propose-integration")
    public ResponseEntity<IntegrationRequestResponseDto> proposeIntegration(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        log.info("User {} proposing integration for fork: {}", user.id(), id);
        return ResponseEntity.ok(playlistService.proposeIntegration(id, user.id()));
    }

    @GetMapping("/{id}/integrations")
    public ResponseEntity<List<IntegrationRequestResponseDto>> getIntegrations(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        log.info("User {} fetching pending integrations for origin: {}", user.id(), id);
        return ResponseEntity.ok(playlistService.getPendingIntegrations(id, user.id()));
    }

}
