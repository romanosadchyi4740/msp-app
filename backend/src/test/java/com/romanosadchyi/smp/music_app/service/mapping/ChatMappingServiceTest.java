package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.UserResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMappingServiceTest {

    @Mock
    UserMappingService userMappingService;

    @Mock
    ChatMessageMappingService messageMappingService;

    @InjectMocks
    ChatMappingService chatMappingService;

    @Test
    void map_WithMessagesAndMemberships_ReturnsDto() {
        UUID chatId = UUID.randomUUID();
        UUID currentUserId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        
        User currentUser = User.builder().id(currentUserId).build();
        User adminUser = User.builder().id(adminUserId).build();

        ChatMembership currentMembership = ChatMembership.builder().user(currentUser).status(ChatStatus.ACCEPTED).isAdmin(false).build();
        ChatMembership adminMembership = ChatMembership.builder().user(adminUser).status(ChatStatus.PENDING).isAdmin(true).build();

        Message msg1 = Message.builder().id(UUID.randomUUID()).timestamp(LocalDateTime.now().minusDays(1)).build();
        Message msg2 = Message.builder().id(UUID.randomUUID()).timestamp(LocalDateTime.now()).build();

        Chat chat = Chat.builder()
                .id(chatId)
                .isGroup(true)
                .name("Group Chat")
                .memberships(new HashSet<>(Set.of(currentMembership, adminMembership)))
                .messages(new HashSet<>(Set.of(msg1, msg2)))
                .build();

        when(userMappingService.map(currentUser)).thenReturn(UserResponseDto.builder().id(currentUserId).build());
        when(userMappingService.map(adminUser)).thenReturn(UserResponseDto.builder().id(adminUserId).build());
        when(messageMappingService.map(msg2)).thenReturn(ChatMessageResponseDto.builder().id(msg2.getId()).build());

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
}
