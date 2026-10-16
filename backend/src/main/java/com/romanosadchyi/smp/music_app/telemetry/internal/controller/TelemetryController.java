package com.romanosadchyi.smp.music_app.telemetry.internal.controller;

import com.romanosadchyi.smp.music_app.shared.security.AuthenticatedUser;
import com.romanosadchyi.smp.music_app.telemetry.internal.dto.ConsensusPollResponse;
import com.romanosadchyi.smp.music_app.telemetry.internal.dto.StatusBroadcastResponse;
import com.romanosadchyi.smp.music_app.telemetry.internal.dto.TelemetryBroadcastRequest;
import com.romanosadchyi.smp.music_app.telemetry.internal.service.PollVoteService;
import com.romanosadchyi.smp.music_app.telemetry.internal.service.StatusBroadcastService;
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

    StatusBroadcastService statusBroadcastService;
    PollVoteService pollVoteService;

    @PostMapping("/broadcast")
    public ResponseEntity<StatusBroadcastResponse> createBroadcast(
            @RequestBody TelemetryBroadcastRequest request,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(statusBroadcastService.createBroadcast(request, user.id()));
    }

    @GetMapping("/feed")
    public ResponseEntity<Page<StatusBroadcastResponse>> getFeed(
            @AuthenticationPrincipal AuthenticatedUser user,
            Pageable pageable) {
        return ResponseEntity.ok(statusBroadcastService.getFeed(user.id(), pageable));
    }

    @PostMapping("/polls/options/{optionId}/vote")
    public ResponseEntity<ConsensusPollResponse> voteOnOption(
            @PathVariable UUID optionId,
            @AuthenticationPrincipal AuthenticatedUser user) {
        return ResponseEntity.ok(pollVoteService.voteOnOption(optionId, user.id()));
    }

    @DeleteMapping("/broadcast/{id}")
    public ResponseEntity<Void> deleteBroadcast(
            @PathVariable UUID id,
            @AuthenticationPrincipal AuthenticatedUser user) {
        statusBroadcastService.deleteBroadcast(id, user.id());
        return ResponseEntity.noContent().build();
    }
}
