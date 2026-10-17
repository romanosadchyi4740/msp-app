package com.romanosadchyi.smp.music_app.chats.internal.service;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.chats.internal.exception.ChatNotFoundException;
import com.romanosadchyi.smp.music_app.chats.internal.mapping.ChatMessageMappingService;
import com.romanosadchyi.smp.music_app.chats.internal.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
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
    ChatRepository chatRepository;
    UserLookup userLookup;

    @Transactional
    public void processMessage(ChatMessageRequestDto request, UUID senderId) {
        Chat chat = messageChatResolver.resolve(request, senderId);

        Message message = Message.builder()
                .chat(chat)
                .senderId(senderId)
                .content(request.content())
                .sharedTrackId(requireSharedTrack(request))
                .sharedTrackTimestamp(request.sharedTrackTimestamp())
                .fileUrl(requireNewAttachment(request))
                .timestamp(LocalDateTime.now())
                .build();
        ChatMessageResponseDto saved = mappingService.map(messageRepository.save(message));
        log.info("Message saved with ID: {} in chat: {}", saved.id(), saved.chatId());

        chatMessageNotifier.deliver(chat, saved);
    }

    /**
     * The history for a user who may read the chat ({@link ChatMemberships#mayRead}).
     *
     * @throws UserNotFoundException      if the user's account does not exist or was deleted
     * @throws ChatNotFoundException      if there is no such chat
     * @throws UserNotAuthorizedException if the user may not read the chat
     */
    @Transactional(readOnly = true)
    public Page<ChatMessageResponseDto> getMessageHistory(UUID chatId, UUID userId, Pageable pageable) {
        if (!userLookup.exists(userId)) {
            throw new UserNotFoundException("User not found: " + userId);
        }
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));
        if (!ChatMemberships.mayRead(chat, userId)) {
            throw new UserNotAuthorizedException("You are not a member of this chat");
        }
        return mappingService.mapPage(messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable));
    }

    /**
     * The attached file's key, or null when none is attached. Only a chat attachment key that no message carries yet
     * is accepted: a message cannot point at another stored file, such as a track, nor re-post an attachment a user
     * once saw, and so get a download URL for it that outlives their access to the original chat.
     */
    private String requireNewAttachment(ChatMessageRequestDto request) {
        String fileUrl = request.fileUrl();
        if (fileUrl == null) {
            return null;
        }
        if (!ChatAttachmentService.isAttachmentKey(fileUrl)) {
            throw new IllegalArgumentException("Only chat attachments can be attached to a message");
        }
        if (messageRepository.existsByFileUrl(fileUrl)) {
            throw new IllegalArgumentException("This attachment is already attached to a message");
        }
        return fileUrl;
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
