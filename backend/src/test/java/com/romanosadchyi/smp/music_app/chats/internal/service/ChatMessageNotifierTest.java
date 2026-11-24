package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageNotifierTest {

    @Mock
    SimpMessagingTemplate messagingTemplate;

    @Mock
    UserLookup userLookup;

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
        when(userLookup.findDeletedIds(anyCollection())).thenReturn(Set.of());

        notifier.deliver(chat, message);

        verify(messagingTemplate).convertAndSendToUser(first.toString(), "/queue/messages", message);
        verify(messagingTemplate).convertAndSendToUser(second.toString(), "/queue/messages", message);
        verifyNoMoreInteractions(messagingTemplate);
    }

    @Test
    void deliver_SkipsDeletedAccounts() {
        UUID active = UUID.randomUUID();
        UUID deleted = UUID.randomUUID();
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        ChatMemberships.add(chat, active, ChatStatus.ACCEPTED, true);
        ChatMemberships.add(chat, deleted, ChatStatus.ACCEPTED, false);
        ChatMessageResponseDto message = new ChatMessageResponseDto(UUID.randomUUID(), chat.getId(), null, "Hi", null, null, null, null);
        when(userLookup.findDeletedIds(anyCollection())).thenReturn(Set.of(deleted));

        notifier.deliver(chat, message);

        verify(messagingTemplate).convertAndSendToUser(active.toString(), "/queue/messages", message);
        verifyNoMoreInteractions(messagingTemplate);
    }
}
