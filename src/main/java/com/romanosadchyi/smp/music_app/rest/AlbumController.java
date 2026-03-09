package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.service.AlbumService;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/albums")
@RequiredArgsConstructor
@FieldDefaults(level = lombok.AccessLevel.PRIVATE, makeFinal = true)
public class AlbumController {

    AlbumService albumService;

    @PostMapping
    public ResponseEntity<AlbumResponseDto> createAlbum(
            @RequestHeader("X-User-Id") UUID authorId, // Temporary authorization imitation
            @RequestBody AlbumCreateDto dto
    ) {
        return ResponseEntity.ok(albumService.createAlbum(authorId, dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlbumResponseDto> getAlbum(@PathVariable UUID id) {
        return ResponseEntity.ok(albumService.getAlbumById(id));
    }

}
