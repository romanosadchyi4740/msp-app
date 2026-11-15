package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessageResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomChatNotifierTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    RoomChatNotifier notifier;

    UUID roomId = UUID.randomUUID();
    RoomMessageResponse message = new RoomMessageResponse().id(UUID.randomUUID()).content("hi");

    @Test
    void publish_SendsToTheRoomsChatTopic() {
        notifier.publish(roomId, message);

        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId + "/chat", message);
    }

    @Test
    void publish_FailedSend_IsNotThrown() {
        doThrow(new MessagingException("broker down")).when(messagingTemplate).convertAndSend(anyString(),
                any(Object.class));

        assertThatCode(() -> notifier.publish(roomId, message)).doesNotThrowAnyException();
    }
}
