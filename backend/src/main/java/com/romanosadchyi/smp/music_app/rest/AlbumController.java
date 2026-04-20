package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.service.AlbumService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AlbumController {

    AlbumService albumService;

    @PostMapping
    public ResponseEntity<AlbumResponseDto> createAlbum(
            @AuthenticationPrincipal User user,
            @RequestBody AlbumCreateDto dto
    ) {
        log.info("Creating album for user: {}", user.getId());
        return ResponseEntity.ok(albumService.createAlbum(user.getId(), dto));
    }

    @GetMapping
    public ResponseEntity<Page<AlbumResponseDto>> getAllAlbums(@PageableDefault(size = 10) Pageable pageable) {
        log.info("Getting all albums");
        return ResponseEntity.ok(albumService.getAllAlbums(pageable));
    }

    @GetMapping("/my")
    public ResponseEntity<List<AlbumResponseDto>> getMyAlbums(@AuthenticationPrincipal User user) {
        log.info("Getting albums for user: {}", user.getId());
        return ResponseEntity.ok(albumService.getAlbumsByAuthor(user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlbumResponseDto> getAlbum(@PathVariable UUID id) {
        log.info("Getting album with ID: {}", id);
        return ResponseEntity.ok(albumService.getAlbumById(id));
    }

    @GetMapping("/search")
    public ResponseEntity<Page<AlbumResponseDto>> searchAlbums(
            @RequestParam("query") String query,
            @RequestParam(value = "userId", required = false) UUID userId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        log.info("Searching albums with query: {} and userId: {}", query, userId);
        return ResponseEntity.ok(albumService.searchAlbums(query, userId, pageable));
    }

}
