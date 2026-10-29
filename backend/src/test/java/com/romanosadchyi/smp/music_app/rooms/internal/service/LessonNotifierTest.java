package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.LessonEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class LessonNotifierTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    LessonNotifier lessonNotifier;

    UUID roomId = UUID.randomUUID();
    LessonEvent event = new LessonEvent(LessonEventType.LESSON_STARTED, UUID.randomUUID(), null, null, null);

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publish_WithoutTransaction_SendsToTheRoomsStateTopicAtOnce() {
        lessonNotifier.publish(roomId, event);

        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId + "/state", event);
    }

    @Test
    void publish_InTransaction_SendsOnlyAfterTheCommit() {
        TransactionSynchronizationManager.initSynchronization();

        lessonNotifier.publish(roomId, event);

        verifyNoInteractions(messagingTemplate);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(messagingTemplate).convertAndSend("/topic/rooms/" + roomId + "/state", event);
    }

    @Test
    void publish_FailedSend_DoesNotFailTheCaller() {
        doThrow(new MessageDeliveryException("broker down")).when(messagingTemplate)
                .convertAndSend("/topic/rooms/" + roomId + "/state", event);

        assertThatCode(() -> lessonNotifier.publish(roomId, event)).doesNotThrowAnyException();
    }
}
