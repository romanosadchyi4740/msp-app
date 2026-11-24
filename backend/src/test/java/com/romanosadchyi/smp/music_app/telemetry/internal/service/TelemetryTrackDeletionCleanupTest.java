package com.romanosadchyi.smp.music_app.telemetry.internal.service;

import com.romanosadchyi.smp.music_app.telemetry.internal.repository.StatusBroadcastRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TelemetryTrackDeletionCleanupTest {

    @Mock
    StatusBroadcastRepository broadcastRepository;

    @InjectMocks
    TelemetryTrackDeletionCleanup cleanup;

    @Test
    void deleteTrackReferences_DetachesTrackFromBroadcasts() {
        UUID trackId = UUID.randomUUID();

        cleanup.deleteTrackReferences(trackId);

        verify(broadcastRepository).detachTrack(trackId);
    }
}
