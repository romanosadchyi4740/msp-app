package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStateDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.BroadcastStatus;
import com.romanosadchyi.smp.music_app.chats.internal.mapping.BroadcastStateMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BroadcastNotifierTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    BroadcastStateMappingService mappingService;

    @InjectMocks
    BroadcastNotifier notifier;

    final UUID chatId = UUID.randomUUID();
    final UUID broadcastId = UUID.randomUUID();
    final BroadcastState state = BroadcastState.builder()
            .broadcastId(broadcastId)
            .chatId(chatId)
            .hostId(UUID.randomUUID())
            .status(BroadcastStatus.ACTIVE)
            .build();
    final BroadcastStateDto dto = BroadcastStateDto.builder().broadcastId(broadcastId).build();

    @Test
    void publish_SendsTheStateToTheBroadcastsTopic() {
        when(mappingService.map(state)).thenReturn(dto);

        notifier.publish(state);

        verify(messagingTemplate).convertAndSend("/topic/broadcast/" + chatId + "/" + broadcastId, dto);
    }

    @Test
    void sendSnapshot_GoesToTheRequestersQueueOnly() {
        UUID userId = UUID.randomUUID();
        when(mappingService.map(state)).thenReturn(dto);

        notifier.sendSnapshot(userId, chatId, broadcastId, state);

        verify(messagingTemplate).convertAndSendToUser(userId.toString(),
                "/queue/broadcast/" + chatId + "/" + broadcastId + "/snapshot", dto);
    }

    @Test
    void sendSnapshot_OfAnUnknownBroadcast_AnswersItEnded() {
        UUID userId = UUID.randomUUID();
        BroadcastStateDto unknown = BroadcastStateDto.builder().status(BroadcastStatus.TERMINATED).build();
        when(mappingService.unknown(broadcastId, chatId)).thenReturn(unknown);

        notifier.sendSnapshot(userId, chatId, broadcastId, null);

        verify(messagingTemplate).convertAndSendToUser(userId.toString(),
                "/queue/broadcast/" + chatId + "/" + broadcastId + "/snapshot", unknown);
    }
}
