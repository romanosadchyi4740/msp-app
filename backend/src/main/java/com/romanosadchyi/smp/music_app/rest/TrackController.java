package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.RecommendedResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.service.TrackService;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/tracks")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TrackController {

    TrackService trackService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void createTrack(@AuthenticationPrincipal AuthenticatedUser user,
                            @ModelAttribute TrackCreateDto dto,
                            @RequestParam("file") MultipartFile file) {
        log.info("Creating track for user: {}", user.id());
        log.debug("TaskCreateDto: {}", dto);
        trackService.createTrack(user.id(), dto, file);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrackResponseDto> getTrackById(@PathVariable UUID id) {
        log.info("Getting track with ID: {}", id);
        return ResponseEntity.ok(trackService.getTrackById(id));
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<String> getTrackUrl(@PathVariable UUID id) {
        log.info("Getting track URL for track with ID: {}", id);
        return ResponseEntity.ok(trackService.getTrackUrl(id));
    }

    @GetMapping
    public ResponseEntity<Page<TrackResponseDto>> getAllTracks(
            @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize
    ) {
        log.info("Getting all tracks");
        return ResponseEntity.ok(trackService.getAllTracks(pageNo, pageSize));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<TrackResponseDto>> searchTracks(
            @RequestParam("query") String query,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize
    ) {
        log.info("Searching tracks with query: {} and userId: {}", query, userId);
        return ResponseEntity.ok(trackService.searchTracks(query, userId, pageNo, pageSize));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> likeTrack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        log.info("User {} liking track {}", user.id(), id);
        trackService.likeTrack(user.id(), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlikeTrack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        log.info("User {} unliking track {}", user.id(), id);
        trackService.unlikeTrack(user.id(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/liked")
    public ResponseEntity<List<TrackResponseDto>> getLikedTracks(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting liked tracks for user {}", user.id());
        return ResponseEntity.ok(trackService.getLikedTracks(user.id()));
    }

    @GetMapping("/genre/{genreId}")
    public ResponseEntity<Page<TrackResponseDto>> getTracksByGenre(
            @PathVariable UUID genreId,
            @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20", required = false) int pageSize
    ) {
        log.info("Getting tracks for genre: {}", genreId);
        return ResponseEntity.ok(trackService.getTracksByGenre(genreId, pageNo, pageSize));
    }

    @GetMapping("/recommended")
    public ResponseEntity<RecommendedResponseDto> getRecommendedTracks(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting recommended tracks for user {}", user.id());
        return ResponseEntity.ok(trackService.getRecommendedTracks(user.id()));
    }
}
