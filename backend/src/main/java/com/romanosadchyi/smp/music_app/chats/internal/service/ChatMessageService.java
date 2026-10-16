package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.chats.internal.mapping.ChatMessageMappingService;
import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
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
    TrackLookup trackLookup;
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
                .sharedTrackId(requireSharedTrack(request))
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
        return mappingService.mapPage(messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable));
    }

    /**
     * The id of the shared track, or null when none is shared.
     */
    private UUID requireSharedTrack(ChatMessageRequestDto request) {
        UUID trackId = request.sharedTrackId();
        if (trackId != null && !trackLookup.exists(trackId)) {
            throw new TrackNotFoundException("Track not found: " + trackId);
        }
        return trackId;
    }
}
