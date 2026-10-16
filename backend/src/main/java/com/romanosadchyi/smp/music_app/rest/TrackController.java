package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.RecommendedResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.service.TrackCatalogService;
import com.romanosadchyi.smp.music_app.service.TrackLikeService;
import com.romanosadchyi.smp.music_app.service.TrackRecommendationService;
import com.romanosadchyi.smp.music_app.service.TrackUploadService;
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

    TrackUploadService trackUploadService;
    TrackCatalogService trackCatalogService;
    TrackLikeService trackLikeService;
    TrackRecommendationService trackRecommendationService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public void createTrack(@AuthenticationPrincipal AuthenticatedUser user,
                            @ModelAttribute TrackCreateDto dto,
                            @RequestParam("file") MultipartFile file) {
        log.info("Creating track for user: {}", user.id());
        log.debug("TaskCreateDto: {}", dto);
        trackUploadService.createTrack(user.id(), dto, file);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TrackResponseDto> getTrackById(@PathVariable UUID id) {
        log.info("Getting track with ID: {}", id);
        return ResponseEntity.ok(trackCatalogService.getTrackById(id));
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<String> getTrackUrl(@PathVariable UUID id) {
        log.info("Getting track URL for track with ID: {}", id);
        return ResponseEntity.ok(trackCatalogService.getTrackUrl(id));
    }

    @GetMapping
    public ResponseEntity<Page<TrackResponseDto>> getAllTracks(
            @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize
    ) {
        log.info("Getting all tracks");
        return ResponseEntity.ok(trackCatalogService.getAllTracks(pageNo, pageSize));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<TrackResponseDto>> searchTracks(
            @RequestParam("query") String query,
            @RequestParam(value = "userId", required = false) UUID userId,
            @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize
    ) {
        log.info("Searching tracks with query: {} and userId: {}", query, userId);
        return ResponseEntity.ok(trackCatalogService.searchTracks(query, userId, pageNo, pageSize));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Void> likeTrack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        log.info("User {} liking track {}", user.id(), id);
        trackLikeService.likeTrack(user.id(), id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> unlikeTrack(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID id) {
        log.info("User {} unliking track {}", user.id(), id);
        trackLikeService.unlikeTrack(user.id(), id);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/liked")
    public ResponseEntity<List<TrackResponseDto>> getLikedTracks(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting liked tracks for user {}", user.id());
        return ResponseEntity.ok(trackLikeService.getLikedTracks(user.id()));
    }

    @GetMapping("/genre/{genreId}")
    public ResponseEntity<Page<TrackResponseDto>> getTracksByGenre(
            @PathVariable UUID genreId,
            @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20", required = false) int pageSize
    ) {
        log.info("Getting tracks for genre: {}", genreId);
        return ResponseEntity.ok(trackCatalogService.getTracksByGenre(genreId, pageNo, pageSize));
    }

    @GetMapping("/recommended")
    public ResponseEntity<RecommendedResponseDto> getRecommendedTracks(@AuthenticationPrincipal AuthenticatedUser user) {
        log.info("Getting recommended tracks for user {}", user.id());
        return ResponseEntity.ok(trackRecommendationService.getRecommendedTracks(user.id()));
    }
}
