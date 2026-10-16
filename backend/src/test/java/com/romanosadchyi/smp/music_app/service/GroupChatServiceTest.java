package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.request.ClusterCreateRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMappingService;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GroupChatServiceTest {

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatMembershipRepository chatMembershipRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    ChatMappingService chatMappingService;

    @InjectMocks
    GroupChatService chatService;

    @Test
    void createCluster_ValidData_CreatesGroupChatAndMemberships() {
        UUID creatorId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        
        
        ClusterCreateRequestDto dto = new ClusterCreateRequestDto("Hacker Group", Set.of(creatorId, participantId));

        when(userLookup.exists(creatorId)).thenReturn(true);
        when(userLookup.exists(participantId)).thenReturn(true);
        
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
    void inviteMembers_AddsOnlyNewUsersAsPending() {
        UUID requester = UUID.randomUUID();
        UUID existing = UUID.randomUUID();
        UUID invited = UUID.randomUUID();
        Chat chat = Chat.builder().id(UUID.randomUUID()).isGroup(true).build();
        ChatMemberships.add(chat, requester, ChatStatus.ACCEPTED, true);
        ChatMemberships.add(chat, existing, ChatStatus.PENDING, false);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(userLookup.exists(invited)).thenReturn(true);

        chatService.inviteMembers(chat.getId(), requester, List.of(existing, invited));

        assertEquals(3, chat.getMemberships().size());
        assertEquals(ChatStatus.PENDING, ChatMemberships.find(chat, invited).orElseThrow().getStatus());
        verify(chatMembershipRepository).saveAll(argThat(memberships -> ((List<?>) memberships).size() == 1));
    }

    @Test
    void inviteMembers_OneOnOneChat_IsRejected() {
        UUID requester = UUID.randomUUID();
        Chat chat = Chat.builder().id(UUID.randomUUID()).isGroup(false).build();
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThrows(IllegalArgumentException.class,
                () -> chatService.inviteMembers(chat.getId(), requester, List.of(UUID.randomUUID())));
    }

    @Test
    void inviteMembers_PendingRequester_IsNotAuthorized() {
        UUID requester = UUID.randomUUID();
        Chat chat = Chat.builder().id(UUID.randomUUID()).isGroup(true).build();
        ChatMemberships.add(chat, requester, ChatStatus.PENDING, false);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));

        assertThrows(UserNotAuthorizedException.class,
                () -> chatService.inviteMembers(chat.getId(), requester, List.of(UUID.randomUUID())));
        verifyNoInteractions(chatMembershipRepository);
    }

    @Test
    void inviteMembers_UnknownUser_AddsNobody() {
        UUID requester = UUID.randomUUID();
        UUID known = UUID.randomUUID();
        UUID unknown = UUID.randomUUID();
        Chat chat = Chat.builder().id(UUID.randomUUID()).isGroup(true).build();
        ChatMemberships.add(chat, requester, ChatStatus.ACCEPTED, true);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(userLookup.exists(known)).thenReturn(true);
        when(userLookup.exists(unknown)).thenReturn(false);

        assertThrows(UserNotFoundException.class,
                () -> chatService.inviteMembers(chat.getId(), requester, List.of(known, unknown)));
        assertEquals(1, chat.getMemberships().size());
        verifyNoInteractions(chatMembershipRepository);
    }
}
