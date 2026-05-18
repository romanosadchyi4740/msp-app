package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.ChatNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.ClusterCreateRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMappingService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatMembershipRepository chatMembershipRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    ChatMappingService chatMappingService;

    @InjectMocks
    ChatService chatService;

    @Test
    void createCluster_ValidData_CreatesGroupChatAndMemberships() {
        UUID creatorId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        
        User creator = User.builder().id(creatorId).build();
        User participant = User.builder().id(participantId).build();
        
        ClusterCreateRequestDto dto = new ClusterCreateRequestDto("Hacker Group", Set.of(creatorId, participantId));

        when(userRepository.findById(creatorId)).thenReturn(Optional.of(creator));
        when(userRepository.findById(participantId)).thenReturn(Optional.of(participant));
        
        when(chatRepository.save(any(Chat.class))).thenAnswer(i -> {
            Chat c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });

        when(chatMappingService.map(any(Chat.class), eq(creatorId))).thenReturn(
            new ChatResponseDto(UUID.randomUUID(), true, "Hacker Group", null, null, creatorId, "ACCEPTED")
        );

        ChatResponseDto response = chatService.createCluster(creatorId, dto);

        assertNotNull(response);
        assertEquals("Hacker Group", response.name());
        assertTrue(response.isGroup());
        verify(chatRepository).save(any(Chat.class));
        verify(chatMembershipRepository).saveAll(any());
    }

    @Test
    void acceptChat_UserIsPendingParticipant_UpdatesStatus() {
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        User user = User.builder().id(userId).build();
        Chat chat = Chat.builder().id(chatId).build();
        ChatMembership membership = ChatMembership.builder()
                .chat(chat)
                .user(user)
                .status(ChatStatus.PENDING)
                .isAdmin(false)
                .build();
        chat.getMemberships().add(membership);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(chatMappingService.map(any(Chat.class), eq(userId))).thenReturn(
            new ChatResponseDto(chatId, false, null, null, null, null, "ACCEPTED")
        );

        ChatResponseDto response = chatService.acceptChat(chatId, userId);

        assertEquals(ChatStatus.ACCEPTED, membership.getStatus());
        assertEquals("ACCEPTED", response.status());
        verify(chatMembershipRepository).save(membership);
    }

    @Test
    void acceptChat_UserIsAdmin_ThrowsIllegalArgumentException() {
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        User user = User.builder().id(userId).build();
        Chat chat = Chat.builder().id(chatId).build();
        ChatMembership membership = ChatMembership.builder()
                .chat(chat)
                .user(user)
                .status(ChatStatus.ACCEPTED)
                .isAdmin(true) // Initiator
                .build();
        chat.getMemberships().add(membership);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        assertThrows(IllegalArgumentException.class, () -> chatService.acceptChat(chatId, userId));
        verify(chatMembershipRepository, never()).save(any(ChatMembership.class));
    }
}
