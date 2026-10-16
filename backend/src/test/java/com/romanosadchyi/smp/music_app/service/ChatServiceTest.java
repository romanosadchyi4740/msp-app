package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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
    UserLookup userLookup;

    @Mock
    ChatMappingService chatMappingService;

    ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(chatRepository,
                new DirectChatResolver(chatRepository, chatMembershipRepository, userLookup), chatMappingService);
    }

    @Test
    void getOrCreateChatDto_NewChat_CreatesChatAndMemberships() {
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();

        when(chatRepository.findChatBetweenUsers(user1Id, user2Id)).thenReturn(Optional.empty());
        when(userLookup.exists(user1Id)).thenReturn(true);
        when(userLookup.exists(user2Id)).thenReturn(true);
        when(chatRepository.save(any(Chat.class))).thenAnswer(i -> i.getArgument(0));

        chatService.getOrCreateChatDto(user1Id, user2Id);

        verify(chatRepository).save(any(Chat.class));
        verify(chatMembershipRepository).saveAll(argThat(memberships -> {
            Set<ChatMembership> set = (Set<ChatMembership>) memberships;
            return set.size() == 2;
        }));
    }

    @Test
    void getUserChats_ReturnsList() {
        UUID userId = UUID.randomUUID();
        when(chatRepository.findAllByParticipantId(userId)).thenReturn(List.of(new Chat()));
        when(chatMappingService.map(any(Chat.class), eq(userId))).thenReturn(
            new ChatResponseDto(UUID.randomUUID(), false, null, null, null, null, "ACCEPTED")
        );

        List<ChatResponseDto> result = chatService.getUserChats(userId);
        assertEquals(1, result.size());
    }
}
