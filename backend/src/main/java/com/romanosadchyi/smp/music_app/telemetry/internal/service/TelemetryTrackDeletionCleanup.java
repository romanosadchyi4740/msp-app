package com.romanosadchyi.smp.music_app.telemetry.internal.service;

import com.romanosadchyi.smp.music_app.music.external.TrackDeletionCleanup;
import com.romanosadchyi.smp.music_app.telemetry.internal.repository.StatusBroadcastRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Detaches a deleted track from the status broadcasts that attached it; the broadcasts stay.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class TelemetryTrackDeletionCleanup implements TrackDeletionCleanup {

    StatusBroadcastRepository broadcastRepository;

    @Override
    public void deleteTrackReferences(UUID trackId) {
        broadcastRepository.detachTrack(trackId);
    }
}
