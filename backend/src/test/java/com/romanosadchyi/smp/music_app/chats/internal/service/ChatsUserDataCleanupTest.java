package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatsUserDataCleanupTest {

    @Mock
    ChatRepository chatRepository;

    @Mock
    UserLookup userLookup;

    @InjectMocks
    ChatsUserDataCleanup cleanup;

    @Captor
    ArgumentCaptor<Collection<UUID>> idsCaptor;

    final UUID userId = UUID.randomUUID();

    final UUID otherId = UUID.randomUUID();

    @Test
    void deleteUserData_GroupWithActiveMembers_RemovesOnlyTheUser() {
        Chat chat = Chat.builder().isGroup(true).build();
        ChatMemberships.add(chat, userId, null, true);
        ChatMembership other = ChatMemberships.add(chat, otherId, null, false);
        when(chatRepository.findAllByParticipantIdForUpdate(userId)).thenReturn(List.of(chat));
        when(userLookup.findDeletedIds(List.of(otherId))).thenReturn(Set.of());

        cleanup.deleteUserData(userId);

        assertThat(chat.getMemberships()).containsExactly(other);
        verify(chatRepository, never()).delete(any());
    }

    @Test
    void deleteUserData_GroupWhoseOtherMembersAreDeleted_DeletesTheChat() {
        Chat chat = Chat.builder().isGroup(true).build();
        ChatMemberships.add(chat, userId, null, true);
        ChatMemberships.add(chat, otherId, null, false);
        when(chatRepository.findAllByParticipantIdForUpdate(userId)).thenReturn(List.of(chat));
        when(userLookup.findDeletedIds(List.of(otherId))).thenReturn(Set.of(otherId));

        cleanup.deleteUserData(userId);

        verify(chatRepository).delete(chat);
    }

    @Test
    void deleteUserData_DirectChatWithActiveMember_KeepsTheUsersMembership() {
        Chat chat = Chat.builder().isGroup(false).build();
        ChatMembership own = ChatMemberships.add(chat, userId, null, true);
        ChatMembership other = ChatMemberships.add(chat, otherId, null, false);
        when(chatRepository.findAllByParticipantIdForUpdate(userId)).thenReturn(List.of(chat));
        when(userLookup.findDeletedIds(List.of(otherId))).thenReturn(Set.of());

        cleanup.deleteUserData(userId);

        assertThat(chat.getMemberships()).containsExactlyInAnyOrder(own, other);
        verify(chatRepository, never()).delete(any());
    }

    @Test
    void deleteUserData_DirectChatWhoseOtherMemberIsDeleted_DeletesTheChat() {
        Chat chat = Chat.builder().isGroup(false).build();
        ChatMemberships.add(chat, userId, null, true);
        ChatMemberships.add(chat, otherId, null, false);
        when(chatRepository.findAllByParticipantIdForUpdate(userId)).thenReturn(List.of(chat));
        when(userLookup.findDeletedIds(List.of(otherId))).thenReturn(Set.of(otherId));

        cleanup.deleteUserData(userId);

        verify(chatRepository).delete(chat);
    }

    @Test
    void deleteUserData_LastMember_DeletesTheChat() {
        Chat chat = Chat.builder().isGroup(true).build();
        ChatMemberships.add(chat, userId, null, true);
        when(chatRepository.findAllByParticipantIdForUpdate(userId)).thenReturn(List.of(chat));
        when(userLookup.findDeletedIds(List.of())).thenReturn(Set.of());

        cleanup.deleteUserData(userId);

        verify(chatRepository).delete(chat);
    }

    @Test
    void deleteUserData_SeveralChats_LooksUpEachOtherMemberOnceInOneCall() {
        Chat first = Chat.builder().isGroup(false).build();
        Chat second = Chat.builder().isGroup(true).build();
        UUID thirdId = UUID.randomUUID();
        ChatMemberships.add(first, userId, null, true);
        ChatMemberships.add(first, otherId, null, false);
        ChatMemberships.add(second, userId, null, true);
        ChatMemberships.add(second, otherId, null, false);
        ChatMemberships.add(second, thirdId, null, false);
        when(chatRepository.findAllByParticipantIdForUpdate(userId)).thenReturn(List.of(first, second));
        when(userLookup.findDeletedIds(anyCollection())).thenReturn(Set.of());

        cleanup.deleteUserData(userId);

        verify(userLookup).findDeletedIds(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(otherId, thirdId);
    }
}
