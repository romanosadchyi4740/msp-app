package com.romanosadchyi.smp.music_app.rest;

import com.romanosadchyi.smp.music_app.model.dto.request.TelemetryBroadcastRequest;
import com.romanosadchyi.smp.music_app.model.dto.response.ConsensusPollResponse;
import com.romanosadchyi.smp.music_app.model.dto.response.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.service.TelemetryService;
import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/telemetry")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TelemetryController {

    TelemetryService telemetryService;

    @PostMapping("/broadcast")
    public ResponseEntity<StatusBroadcastResponse> createBroadcast(
            @RequestBody TelemetryBroadcastRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(telemetryService.createBroadcast(request, user.id()));
    }

    @GetMapping("/feed")
    public ResponseEntity<Page<StatusBroadcastResponse>> getFeed(
            @AuthenticationPrincipal AuthenticatedUser user,
            Pageable pageable) {
        return ResponseEntity.ok(telemetryService.getFeed(user.id(), pageable));
    }

    @PostMapping("/polls/options/{optionId}/vote")
    public ResponseEntity<ConsensusPollResponse> voteOnOption(
            @PathVariable UUID optionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(telemetryService.voteOnOption(optionId, user.id()));
    }

    @DeleteMapping("/broadcast/{id}")
    public ResponseEntity<Void> deleteBroadcast(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        telemetryService.deleteBroadcast(id, user.id());
        return ResponseEntity.noContent().build();
    }
}
