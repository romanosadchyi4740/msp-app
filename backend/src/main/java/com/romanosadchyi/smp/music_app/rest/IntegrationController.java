package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.response.IntegrationRequestResponseDto;
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
@RequestMapping("/api/integrations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class IntegrationController {

    PlaylistService playlistService;

    @PostMapping("/{id}/resolve")
    public ResponseEntity<IntegrationRequestResponseDto> resolveIntegration(
            @PathVariable UUID id,
            @RequestParam("action") String action,
            @AuthenticationPrincipal User user
    ) {
        log.info("User {} resolving integration request {} with action: {}", user.getId(), id, action);
        return ResponseEntity.ok(playlistService.resolveIntegration(id, action, user.getId()));
    }
}