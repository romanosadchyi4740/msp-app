package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.dto.request.TelemetryBroadcastRequest;
import com.romanosadchyi.smp.music_app.model.dto.response.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.service.TelemetryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
public class TelemetryController {

    private final TelemetryService telemetryService;

    @PostMapping("/broadcast")
    public ResponseEntity<StatusBroadcastResponse> createBroadcast(
            @RequestBody TelemetryBroadcastRequest request,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(telemetryService.createBroadcast(request, user.getId()));
    }

    @GetMapping("/feed")
    public ResponseEntity<Page<StatusBroadcastResponse>> getFeed(
            @AuthenticationPrincipal User user,
            Pageable pageable) {
        return ResponseEntity.ok(telemetryService.getFeed(user.getId(), pageable));
    }

    @PostMapping("/polls/options/{optionId}/vote")
    public ResponseEntity<com.romanosadchyi.smp.music_app.model.dto.response.ConsensusPollResponse> voteOnOption(
            @PathVariable UUID optionId,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(telemetryService.voteOnOption(optionId, user.getId()));
    }

    @DeleteMapping("/broadcast/{id}")
    public ResponseEntity<Void> deleteBroadcast(
            @PathVariable UUID id,
            @AuthenticationPrincipal User user) {
        telemetryService.deleteBroadcast(id, user.getId());
        return ResponseEntity.noContent().build();
    }
}
