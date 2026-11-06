package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.shared.util.AfterCommit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sends participant events to the room's participants topic after the commit. A failed send is logged, not thrown:
 * the change is committed, and clients read the participants again through {@code GET /api/rooms/{id}}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ParticipantNotifier {

    SimpMessagingTemplate messagingTemplate;

    public void publish(UUID roomId, ParticipantEvent event) {
        AfterCommit.run(() -> {
            try {
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/participants", event);
            } catch (RuntimeException e) {
                log.warn("Could not send {} to room {}", event.type(), roomId, e);
            }
        });
    }
}
