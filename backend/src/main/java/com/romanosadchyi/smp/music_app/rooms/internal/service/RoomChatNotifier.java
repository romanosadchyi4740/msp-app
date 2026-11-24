package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessageResponse;
import com.romanosadchyi.smp.music_app.shared.util.AfterCommit;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Sends a stored chat message to the room's chat topic after the commit (KAN-51). A failed send is logged without
 * the content, not thrown: the message is stored, and clients read the history again when they reconnect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomChatNotifier {

    SimpMessagingTemplate messagingTemplate;

    public void publish(UUID roomId, RoomMessageResponse message) {
        AfterCommit.run(() -> {
            try {
                messagingTemplate.convertAndSend("/topic/rooms/" + roomId + "/chat", message);
            } catch (RuntimeException e) {
                log.warn("Could not send chat message {} to room {}", message.getId(), roomId, e);
            }
        });
    }
}
