package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @Mock
    ChatMessageMappingService messageMappingService;

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

        when(userLookup.getProfile(currentUserId)).thenReturn(UserResponseDto.builder().id(currentUserId).build());
        when(userLookup.getProfile(adminUserId)).thenReturn(UserResponseDto.builder().id(adminUserId).build());
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
}
