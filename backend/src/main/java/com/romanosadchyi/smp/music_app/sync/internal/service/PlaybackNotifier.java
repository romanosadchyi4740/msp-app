package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.RoomSyncSnapshot;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

/**
 * Sends playback to the room and snapshots to one participant. Inside a transaction a message goes out after the
 * commit, so nobody hears of a state that was rolled back. A failed send is logged, not thrown: the change is
 * committed, and clients catch up through the next snapshot.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaybackNotifier {

    SimpMessagingTemplate messagingTemplate;

    /**
     * To {@code /topic/rooms/{roomId}/state}, which every participant subscribes to.
     */
    public void publishState(UUID roomId, PlaybackStateDto state) {
        afterCommit(() -> send("/topic/rooms/" + roomId + "/state", state));
    }

    /**
     * To {@code /user/queue/rooms/{roomId}/snapshot} of this user only.
     */
    public void sendSnapshot(UUID userId, RoomSyncSnapshot snapshot) {
        afterCommit(() -> {
            try {
                messagingTemplate.convertAndSendToUser(userId.toString(),
                        "/queue/rooms/" + snapshot.roomId() + "/snapshot", snapshot);
            } catch (RuntimeException e) {
                log.warn("Could not send the snapshot of room {} to user {}", snapshot.roomId(), userId, e);
            }
        });
    }

    private void send(String destination, Object payload) {
        try {
            messagingTemplate.convertAndSend(destination, payload);
        } catch (RuntimeException e) {
            log.warn("Could not send to {}", destination, e);
        }
    }

    private static void afterCommit(Runnable send) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send.run();
            }
        });
    }
}
