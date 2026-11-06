package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.RoomSyncSnapshot;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PlaybackNotifierTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    PlaybackNotifier playbackNotifier;

    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    PlaybackStateDto state = new PlaybackStateDto(UUID.randomUUID(), 1, 0, 120, 4, 4, PlaybackStatus.STOPPED, 0L,
            false, 1L, 0L);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishState_OutsideATransaction_SendsToTheRoomsStateTopic() {
        playbackNotifier.publishState(roomId, state);

        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId + "/state", state);
    }

    @Test
    void publishState_InsideATransaction_WaitsForTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        playbackNotifier.publishState(roomId, state);

        verifyNoInteractions(messagingTemplate);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId + "/state", state);
    }

    @Test
    void sendSnapshot_GoesToTheUsersSnapshotQueue() {
        RoomSyncSnapshot snapshot = new RoomSyncSnapshot(roomId, null, null, 5L);

        playbackNotifier.sendSnapshot(userId, snapshot);

        verify(messagingTemplate).convertAndSendToUser(userId.toString(), "/queue/rooms/" + roomId + "/snapshot",
                snapshot);
    }

    @Test
    void failedSend_IsNotThrown() {
        doThrow(new MessagingException("broker down")).when(messagingTemplate).convertAndSend(anyString(),
                any(Object.class));
        doThrow(new MessagingException("broker down")).when(messagingTemplate).convertAndSendToUser(anyString(),
                anyString(), any(Object.class));

        assertThatCode(() -> playbackNotifier.publishState(roomId, state)).doesNotThrowAnyException();
        assertThatCode(() -> playbackNotifier.sendSnapshot(userId, new RoomSyncSnapshot(roomId, null, null, 0L)))
                .doesNotThrowAnyException();
    }
}
