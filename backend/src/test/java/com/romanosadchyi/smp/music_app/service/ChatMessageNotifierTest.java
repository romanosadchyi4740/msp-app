package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
class ChatMessageNotifierTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    ChatMessageNotifier notifier;

    @Test
    void deliver_SendsToEveryMember() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        ChatMemberships.add(chat, first, ChatStatus.ACCEPTED, true);
        ChatMemberships.add(chat, second, ChatStatus.PENDING, false);
        ChatMessageResponseDto message = new ChatMessageResponseDto(UUID.randomUUID(), chat.getId(), null, "Hi", null, null, null, null);

        notifier.deliver(chat, message);

        verify(messagingTemplate).convertAndSendToUser(first.toString(), "/queue/messages", message);
        verify(messagingTemplate).convertAndSendToUser(second.toString(), "/queue/messages", message);
        verifyNoMoreInteractions(messagingTemplate);
    }
}
