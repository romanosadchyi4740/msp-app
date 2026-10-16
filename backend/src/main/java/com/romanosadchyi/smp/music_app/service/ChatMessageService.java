package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.model.dto.request.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMessageMappingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sending chat messages (stored, then delivered to every member) and reading a chat's history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageService {

    MessageChatResolver messageChatResolver;
    TrackRepository trackRepository;
    MessageRepository messageRepository;
    ChatMessageMappingService mappingService;
    ChatMessageNotifier chatMessageNotifier;

    @Transactional
    public void processMessage(ChatMessageRequestDto request, UUID senderId) {
        Chat chat = messageChatResolver.resolve(request, senderId);

        Message message = Message.builder()
                .chat(chat)
                .senderId(senderId)
                .content(request.content())
                .sharedTrack(findSharedTrack(request))
                .sharedTrackTimestamp(request.sharedTrackTimestamp())
                .fileUrl(request.fileUrl())
                .timestamp(LocalDateTime.now())
                .build();
        ChatMessageResponseDto saved = mappingService.map(messageRepository.save(message));
        log.info("Message saved with ID: {} in chat: {}", saved.id(), saved.chatId());

        chatMessageNotifier.deliver(chat, saved);
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponseDto> getMessageHistory(UUID chatId, Pageable pageable) {
        return messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable)
                .map(mappingService::map);
    }

    private Track findSharedTrack(ChatMessageRequestDto request) {
        if (request.sharedTrackId() == null) {
            return null;
        }
        return trackRepository.findById(request.sharedTrackId())
                .orElseThrow(() -> new TrackNotFoundException("Track not found: " + request.sharedTrackId()));
    }
}
