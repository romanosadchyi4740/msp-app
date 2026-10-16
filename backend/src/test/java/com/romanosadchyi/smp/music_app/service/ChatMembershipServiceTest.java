package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMembershipDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMappingService;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserRole;
import com.romanosadchyi.smp.music_app.users.external.UserSummary;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMembershipServiceTest {

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatMembershipRepository chatMembershipRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    ChatMappingService chatMappingService;

    @InjectMocks
    ChatMembershipService chatService;

    @Test
    void acceptChat_UserIsPendingParticipant_UpdatesStatus() {
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        Chat chat = Chat.builder().id(chatId).build();
        ChatMembership membership = ChatMembership.builder()
                .chat(chat)
                .userId(userId)
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
        
        Chat chat = Chat.builder().id(chatId).build();
        ChatMembership membership = ChatMembership.builder()
                .chat(chat)
                .userId(userId)
                .status(ChatStatus.ACCEPTED)
                .isAdmin(true) // Initiator
                .build();
        chat.getMemberships().add(membership);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        assertThrows(IllegalArgumentException.class, () -> chatService.acceptChat(chatId, userId));
        verify(chatMembershipRepository, never()).save(any(ChatMembership.class));
    }

    @Test
    void rejectChat_UserIsParticipant_UpdatesStatusToRejected() {
        UUID chatId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        
        Chat chat = Chat.builder().id(chatId).build();
        ChatMembership membership = ChatMembership.builder()
                .chat(chat)
                .userId(userId)
                .status(ChatStatus.PENDING)
                .build();
        chat.getMemberships().add(membership);

        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));

        chatService.rejectChat(chatId, userId);

        assertEquals(ChatStatus.REJECTED, membership.getStatus());
        verify(chatMembershipRepository).save(membership);
    }

    @Test
    void getChatMembers_ReturnsMembersWithNames() {
        UUID requester = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        ChatMemberships.add(chat, requester, ChatStatus.ACCEPTED, true);
        ChatMemberships.add(chat, other, ChatStatus.PENDING, false);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(userLookup.findSummaries(anyList())).thenReturn(Map.of(
                requester, new UserSummary(requester, "Me", "me.png", UserRole.ROLE_USER),
                other, new UserSummary(other, "Other", null, UserRole.ROLE_USER)));

        List<ChatMembershipDto> members = chatService.getChatMembers(chat.getId(), requester);

        assertEquals(2, members.size());
        ChatMembershipDto otherDto = members.stream().filter(m -> m.userId().equals(other)).findFirst().orElseThrow();
        assertEquals("Other", otherDto.displayName());
        assertEquals(ChatStatus.PENDING, otherDto.status());
    }

    @Test
    void getChatMembers_NotAMember_IsNotAuthorized() {
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        ChatMemberships.add(chat, UUID.randomUUID(), ChatStatus.ACCEPTED, true);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThrows(UserNotAuthorizedException.class, () -> chatService.getChatMembers(chat.getId(), UUID.randomUUID()));
        verifyNoInteractions(userLookup);
    }
}
