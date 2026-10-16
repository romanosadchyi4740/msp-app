package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatStatus;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.chats.internal.mapping.ChatMessageMappingService;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    ChatRepository chatRepository;

    @Mock
    ChatMembershipRepository chatMembershipRepository;

    @Mock
    MessageRepository messageRepository;

    @Mock
    UserLookup userLookup;

    @Mock
    TrackLookup trackLookup;

    @Mock
    ChatMessageMappingService mappingService;

    @Mock
    SimpMessagingTemplate messagingTemplate;

    ChatMessageService chatService;

    @BeforeEach
    void setUp() {
        DirectChatResolver directChatResolver = new DirectChatResolver(chatRepository, chatMembershipRepository, userLookup);
        MessageChatResolver messageChatResolver =
                new MessageChatResolver(chatRepository, chatMembershipRepository, directChatResolver, userLookup);
        chatService = new ChatMessageService(messageChatResolver, trackLookup, messageRepository, mappingService,
                new ChatMessageNotifier(messagingTemplate));
    }

    @Test
    void processMessage_ValidData_SavesAndBroadcasts() {
        UUID senderId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        Chat chat = Chat.builder().id(chatId).build();
        ChatMembership membership = ChatMembership.builder().chat(chat).userId(senderId).status(ChatStatus.ACCEPTED).build();
        chat.getMemberships().add(membership);

        ChatMessageRequestDto request = new ChatMessageRequestDto(chatId, null, "Hello", null, null, null);

        when(userLookup.exists(senderId)).thenReturn(true);
        when(chatRepository.findById(chatId)).thenReturn(Optional.of(chat));
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));
        when(mappingService.map(any(Message.class))).thenReturn(
            new ChatMessageResponseDto(UUID.randomUUID(), chatId, null, "Hello", null, null, null, null)
        );

        chatService.processMessage(request, senderId);

        verify(messageRepository).save(any(Message.class));
        verify(messagingTemplate).convertAndSendToUser(eq(senderId.toString()), eq("/queue/messages"), any(ChatMessageResponseDto.class));
    }

    @Test
    void getMessageHistory_ReturnsPage() {
        UUID chatId = UUID.randomUUID();
        Pageable pageable = PageRequest.of(0, 10);
        Page<Message> page = new PageImpl<>(List.of(new Message()));

        when(messageRepository.findByChatIdOrderByTimestampDesc(eq(chatId), any(Pageable.class))).thenReturn(page);
        when(mappingService.mapPage(page)).thenReturn(new PageImpl<>(List.of(
            new ChatMessageResponseDto(UUID.randomUUID(), chatId, null, "Hello", null, null, null, null)
        )));

        Page<ChatMessageResponseDto> result = chatService.getMessageHistory(chatId, pageable);
        assertEquals(1, result.getTotalElements());
        verify(mappingService, never()).map(any(Message.class));
    }

    @Test
    void processMessage_WithSharedTrack_StoresItsId() {
        UUID senderId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Chat chat = chatWithMember(senderId);
        when(userLookup.exists(senderId)).thenReturn(true);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(trackLookup.exists(trackId)).thenReturn(true);
        when(messageRepository.save(any(Message.class))).thenAnswer(i -> i.getArgument(0));
        when(mappingService.map(any(Message.class))).thenReturn(
            new ChatMessageResponseDto(UUID.randomUUID(), chat.getId(), null, "Hi", null, null, 5, null)
        );

        chatService.processMessage(new ChatMessageRequestDto(chat.getId(), null, "Hi", trackId, 5, null), senderId);

        verify(messageRepository).save(argThat(m -> trackId.equals(m.getSharedTrackId()) && m.getSharedTrackTimestamp() == 5));
    }

    @Test
    void processMessage_UnknownSharedTrack_IsNotFound() {
        UUID senderId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Chat chat = chatWithMember(senderId);
        when(userLookup.exists(senderId)).thenReturn(true);
        when(chatRepository.findById(chat.getId())).thenReturn(Optional.of(chat));
        when(trackLookup.exists(trackId)).thenReturn(false);

        ChatMessageRequestDto request = new ChatMessageRequestDto(chat.getId(), null, "Hi", trackId, null, null);
        assertThrows(TrackNotFoundException.class, () -> chatService.processMessage(request, senderId));
        verify(messageRepository, never()).save(any());
        verifyNoInteractions(messagingTemplate);
    }

    private static Chat chatWithMember(UUID userId) {
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        chat.getMemberships().add(ChatMembership.builder().chat(chat).userId(userId).status(ChatStatus.ACCEPTED).build());
        return chat;
    }
}
