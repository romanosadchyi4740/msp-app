package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.AlbumCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.AlbumResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.service.AlbumService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

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
        return ResponseEntity.ok(albumService.createAlbum(user.getId(), dto));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlbumResponseDto> getAlbum(@PathVariable UUID id) {
        return ResponseEntity.ok(albumService.getAlbumById(id));
    }

}
