package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMappingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The chats a user takes part in, and opening a one-on-one chat with another user.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatService {

    ChatRepository chatRepository;
    DirectChatResolver directChatResolver;
    ChatMappingService chatMappingService;

    @Transactional(readOnly = true)
    public List<ChatResponseDto> getUserChats(UUID userId) {
        return chatRepository.findAllByParticipantId(userId).stream()
                .map(chat -> chatMappingService.map(chat, userId))
                .toList();
    }

    @Transactional
    public ChatResponseDto getOrCreateChatDto(UUID userId, UUID recipientId) {
        return chatMappingService.map(directChatResolver.getOrCreate(userId, recipientId), userId);
    }
}
