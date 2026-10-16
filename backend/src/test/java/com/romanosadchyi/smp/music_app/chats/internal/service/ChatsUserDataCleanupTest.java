package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatsUserDataCleanupTest {

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatMembershipRepository chatMembershipRepository;

    @Mock
    MessageRepository messageRepository;

    @InjectMocks
    ChatsUserDataCleanup cleanup;

    final UUID userId = UUID.randomUUID();

    @Test
    void deleteUserData_ChatWithOtherMembers_RemovesOnlyTheUser() {
        UUID otherId = UUID.randomUUID();
        Chat chat = new Chat();
        ChatMembership own = ChatMemberships.add(chat, userId, null, true);
        ChatMembership other = ChatMemberships.add(chat, otherId, null, false);
        when(chatMembershipRepository.findAllByUserId(userId)).thenReturn(List.of(own));

        cleanup.deleteUserData(userId);

        assertThat(chat.getMemberships()).containsExactly(other);
        verify(chatRepository, never()).delete(any());
    }

    @Test
    void deleteUserData_LastMember_DeletesTheChat() {
        Chat chat = new Chat();
        ChatMembership own = ChatMemberships.add(chat, userId, null, true);
        when(chatMembershipRepository.findAllByUserId(userId)).thenReturn(List.of(own));

        cleanup.deleteUserData(userId);

        verify(chatRepository).delete(chat);
    }

    @Test
    void deleteUserData_DeletesSentMessagesAfterTheChats() {
        Chat chat = new Chat();
        ChatMembership own = ChatMemberships.add(chat, userId, null, true);
        when(chatMembershipRepository.findAllByUserId(userId)).thenReturn(List.of(own));

        cleanup.deleteUserData(userId);

        InOrder order = inOrder(chatRepository, messageRepository);
        order.verify(chatRepository).delete(chat);
        order.verify(messageRepository).deleteAllBySenderId(userId);
    }
}
