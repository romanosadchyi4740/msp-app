package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEvent;
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
 * Sends lesson events to the room's state topic. Inside a transaction the event goes out after the commit, so
 * participants never hear of a change that was rolled back.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class LessonNotifier {

    SimpMessagingTemplate messagingTemplate;

    public void publish(UUID roomId, LessonEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            send(roomId, event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(roomId, event);
            }
        });
    }

    /**
     * The change is already committed, so a failed send must not fail the request that made it; clients catch up
     * through the snapshot on their next sync.
     */
    private void send(UUID roomId, LessonEvent event) {
        try {
            messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/state", event);
        } catch (RuntimeException e) {
            log.warn("Could not send {} to room {}", event.type(), roomId, e);
        }
    }
}
