package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
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
class ParticipantNotifierTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    ParticipantNotifier participantNotifier;

    UUID roomId = UUID.randomUUID();
    ParticipantEvent event = new ParticipantEvent(ParticipantEventType.JOINED, UUID.randomUUID(), UUID.randomUUID());

    @Test
    void publish_SendsToTheRoomsParticipantsTopic() {
        participantNotifier.publish(roomId, event);

        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId + "/participants", event);
    }

    @Test
    void publish_FailedSend_IsNotThrown() {
        doThrow(new MessagingException("broker down")).when(messagingTemplate).convertAndSend(anyString(),
                any(Object.class));

        assertThatCode(() -> participantNotifier.publish(roomId, event)).doesNotThrowAnyException();
    }
}
