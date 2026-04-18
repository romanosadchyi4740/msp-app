package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.AddTrackToPlaylistDto;
import com.romanosadchyi.smp.music_app.model.dto.request.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.service.PlaylistService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @PostMapping("/{playlistId}/tracks")
    public ResponseEntity<PlaylistResponseDto> addTrack(
            @AuthenticationPrincipal User user,
            @PathVariable UUID playlistId,
            @RequestBody AddTrackToPlaylistDto dto
    ) {
        log.info("Adding track to playlist: {}", playlistId);
        return ResponseEntity.ok(playlistService.addTrackToPlaylist(user.getId(), playlistId, dto.trackId()));
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

}
