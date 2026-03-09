package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.AddTrackToPlaylistDto;
import com.romanosadchyi.smp.music_app.model.dto.request.PlaylistCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.PlaylistResponseDto;
import com.romanosadchyi.smp.music_app.service.PlaylistService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaylistController {

    PlaylistService playlistService;

    @PostMapping
    public ResponseEntity<PlaylistResponseDto> createPlaylist(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestBody PlaylistCreateDto dto
    ) {
        return ResponseEntity.ok(playlistService.createPlaylist(userId, dto));
    }

    @PostMapping("/{playlistId}/tracks")
    public ResponseEntity<PlaylistResponseDto> addTrack(
            @RequestHeader("X-User-Id") UUID userId,
            @PathVariable UUID playlistId,
            @RequestBody AddTrackToPlaylistDto dto
    ) {
        return ResponseEntity.ok(playlistService.addTrackToPlaylist(userId, playlistId, dto.trackId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlaylistResponseDto> getPlaylist(
            @PathVariable UUID id,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId
    ) {
        return ResponseEntity.ok(playlistService.getPlaylistById(id, userId));
    }

}
