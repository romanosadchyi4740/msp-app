package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LegacyTrackDeletionCleanupTest {

    @Mock
    MessageRepository messageRepository;

    @InjectMocks
    LegacyTrackDeletionCleanup cleanup;

    @Test
    void deleteTrackReferences_DetachesTrackFromMessages() {
        UUID trackId = UUID.randomUUID();

        cleanup.deleteTrackReferences(trackId);

        verify(messageRepository).detachTrack(trackId);
    }
}
