package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.request.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import com.romanosadchyi.smp.music_app.model.enums.ChatStatus;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMessageMappingService;
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
    TrackRepository trackRepository;

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
        chatService = new ChatMessageService(messageChatResolver, trackRepository, messageRepository, mappingService,
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
        when(mappingService.map(any(Message.class))).thenReturn(
            new ChatMessageResponseDto(UUID.randomUUID(), chatId, null, "Hello", null, null, null, null)
        );

        Page<ChatMessageResponseDto> result = chatService.getMessageHistory(chatId, pageable);
        assertEquals(1, result.getTotalElements());
    }
}
