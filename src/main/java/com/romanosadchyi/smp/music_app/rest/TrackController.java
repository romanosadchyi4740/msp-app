package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.TrackCreateDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.service.TrackService;
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
    public void createTrack(@AuthenticationPrincipal User user,
                            @ModelAttribute TrackCreateDto dto,
                            @RequestParam("file") MultipartFile file) {
        log.debug("TaskCreateDto: {}", dto);
        trackService.createTrack(user.getId(), dto, file);
    }

    @GetMapping("/{id}/url")
    public ResponseEntity<String> getTrackUrl(@PathVariable UUID id) {
        log.debug("Getting track URL for track with ID: {}", id);
        return ResponseEntity.ok(trackService.getTrackUrl(id));
    }

    @GetMapping
    public ResponseEntity<Page<TrackResponseDto>> getAllTracks(
            @RequestParam(value = "pageNo", defaultValue = "0", required = false) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "10", required = false) int pageSize
    ) {
        log.debug("Getting all tracks");
        return ResponseEntity.ok(trackService.getAllTracks(pageNo, pageSize));
    }

}
