package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.chats.internal.service.ChatMemberships;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @Mock
    ChatMessageMappingService messageMappingService;

    @Captor
    ArgumentCaptor<Collection<UUID>> idsCaptor;

    @InjectMocks
    ChatMappingService chatMappingService;

    @Test
    void map_WithMessagesAndMemberships_ReturnsDto() {
        UUID chatId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();

        ChatMembership currentMembership = ChatMembership.builder().userId(currentUserId).status(ChatStatus.ACCEPTED).isAdmin(false).build();
        ChatMembership adminMembership = ChatMembership.builder().userId(adminUserId).status(ChatStatus.PENDING).isAdmin(true).build();

        Message msg1 = Message.builder().id(UUID.randomUUID()).timestamp(LocalDateTime.now().minusDays(1)).build();
        Message msg2 = Message.builder().id(UUID.randomUUID()).timestamp(LocalDateTime.now()).build();

        Chat chat = Chat.builder()
                .id(chatId)
                .isGroup(true)
                .name("Group Chat")
                .memberships(new HashSet<>(Set.of(currentMembership, adminMembership)))
                .messages(new HashSet<>(Set.of(msg1, msg2)))
                .build();

        when(userLookup.findProfiles(anyCollection())).thenReturn(Map.of(
                currentUserId, UserResponseDto.builder().id(currentUserId).build(),
                adminUserId, UserResponseDto.builder().id(adminUserId).build()));
        when(messageMappingService.mapAll(List.of(msg2))).thenReturn(List.of(ChatMessageResponseDto.builder().id(msg2.getId()).build()));

        ChatResponseDto result = chatMappingService.map(chat, currentUserId);

        assertNotNull(result);
        assertEquals(chatId, result.id());
        assertTrue(result.isGroup());
        assertEquals("Group Chat", result.name());
        assertEquals(2, result.participants().size());
        assertEquals(msg2.getId(), result.lastMessage().id());
        assertEquals(adminUserId, result.initiatorId());
        assertEquals("ACCEPTED", result.status());
    }

    @Test
    void map_EmptyChat_ReturnsDto() {
        UUID chatId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        Chat chat = Chat.builder().id(chatId).build();

        ChatResponseDto result = chatMappingService.map(chat, currentUserId);

        assertNotNull(result);
        assertFalse(result.isGroup());
        assertNull(result.lastMessage());
        assertNull(result.initiatorId());
        assertEquals("PENDING", result.status());
    }

    @Test
    void mapAll_MapsTheLastMessagesOfAllChatsTogether() {
        UUID currentUserId = UUID.randomUUID();
        Message older = Message.builder().id(UUID.randomUUID()).timestamp(LocalDateTime.now().minusDays(1)).build();
        Message last1 = Message.builder().id(UUID.randomUUID()).timestamp(LocalDateTime.now()).build();
        Message last2 = Message.builder().id(UUID.randomUUID()).timestamp(LocalDateTime.now()).build();
        Chat chat1 = Chat.builder().id(UUID.randomUUID()).messages(new HashSet<>(Set.of(older, last1))).build();
        Chat empty = Chat.builder().id(UUID.randomUUID()).build();
        Chat chat2 = Chat.builder().id(UUID.randomUUID()).messages(new HashSet<>(Set.of(last2))).build();
        ChatMessageResponseDto dto1 = ChatMessageResponseDto.builder().id(last1.getId()).build();
        ChatMessageResponseDto dto2 = ChatMessageResponseDto.builder().id(last2.getId()).build();
        when(messageMappingService.mapAll(List.of(last1, last2))).thenReturn(List.of(dto1, dto2));

        List<ChatResponseDto> result = chatMappingService.mapAll(List.of(chat1, empty, chat2), currentUserId);

        assertEquals(dto1, result.get(0).lastMessage());
        assertNull(result.get(1).lastMessage());
        assertEquals(dto2, result.get(2).lastMessage());
        verify(messageMappingService).mapAll(any());
        verify(messageMappingService, never()).map(any());
    }

    @Test
    void mapAll_LooksUpAllParticipantsOnce() {
        UUID currentUserId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        UUID thirdId = UUID.randomUUID();
        Chat first = Chat.builder().id(UUID.randomUUID()).build();
        Chat second = Chat.builder().id(UUID.randomUUID()).isGroup(true).build();
        ChatMemberships.add(first, currentUserId, ChatStatus.ACCEPTED, true);
        ChatMemberships.add(first, otherId, ChatStatus.ACCEPTED, false);
        ChatMemberships.add(second, currentUserId, ChatStatus.ACCEPTED, true);
        ChatMemberships.add(second, otherId, ChatStatus.ACCEPTED, false);
        ChatMemberships.add(second, thirdId, ChatStatus.PENDING, false);
        UserResponseDto other = UserResponseDto.builder().id(otherId).build();
        when(userLookup.findProfiles(anyCollection())).thenReturn(Map.of(
                currentUserId, UserResponseDto.builder().id(currentUserId).build(),
                otherId, other,
                thirdId, UserResponseDto.builder().id(thirdId).build()));

        List<ChatResponseDto> result = chatMappingService.mapAll(List.of(first, second), currentUserId);

        assertThat(result.get(0).participants()).hasSize(2).contains(other);
        assertThat(result.get(1).participants()).hasSize(3).contains(other);
        verify(userLookup).findProfiles(idsCaptor.capture());
        assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(currentUserId, otherId, thirdId);
        verify(userLookup, never()).getProfile(any());
    }
}
