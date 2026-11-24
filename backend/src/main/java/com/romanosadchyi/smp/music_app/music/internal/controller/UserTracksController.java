package com.romanosadchyi.smp.music_app.music.internal.controller;

import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.service.TrackCatalogService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * A user's tracks. Served from here rather than from {@code UserController} so that {@code users} does not depend
 * on tracks.
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserTracksController {

    TrackCatalogService trackCatalogService;

    @GetMapping("/{id}/tracks")
    public ResponseEntity<List<TrackResponseDto>> getUserTracks(@PathVariable UUID id) {
        log.info("Getting tracks for user with ID: {}", id);
        return ResponseEntity.ok(trackCatalogService.getTracksByAuthor(id));
    }
}
