package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.ChatNotFoundException;
import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.ChatMessageRequest;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponse;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMessageMappingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final TrackRepository trackRepository;
    private final ChatMessageMappingService mappingService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void processMessage(ChatMessageRequest request, UUID senderId) {
        ChatMessageResponse savedMessage = saveMessage(request, senderId);
        log.info("Message saved with ID: {} in chat: {}", savedMessage.id(), savedMessage.chatId());

        Chat chat = chatRepository.findById(savedMessage.chatId())
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        chat.getParticipants().forEach(participant -> {
            messagingTemplate.convertAndSendToUser(
                    participant.getId().toString(),
                    "/queue/messages",
                    savedMessage
            );
        });
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponse> getMessageHistory(UUID chatId, Pageable pageable) {
        return messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable)
                .map(mappingService::map);
    }

    private ChatMessageResponse saveMessage(ChatMessageRequest request, UUID senderId) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + senderId));

        Chat chat;
        if (request.chatId() != null) {
            chat = chatRepository.findById(request.chatId())
                    .orElseThrow(() -> new ChatNotFoundException("Chat not found"));
        } else if (request.recipientId() != null) {
            chat = getOrCreateChat(senderId, request.recipientId());
        } else {
            throw new IllegalArgumentException("Either chatId or recipientId must be provided");
        }

        Track sharedTrack = null;
        if (request.sharedTrackId() != null) {
            sharedTrack = trackRepository.findById(request.sharedTrackId())
                    .orElseThrow(() -> new TrackNotFoundException("Track not found: " + request.sharedTrackId()));
        }

        Message message = Message.builder()
                .chat(chat)
                .sender(sender)
                .content(request.content())
                .sharedTrack(sharedTrack)
                .build();

        return mappingService.map(messageRepository.save(message));
    }

    private Chat getOrCreateChat(UUID user1Id, UUID user2Id) {
        return chatRepository.findChatBetweenUsers(user1Id, user2Id)
                .orElseGet(() -> {
                    User user1 = userRepository.findById(user1Id)
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + user1Id));
                    User user2 = userRepository.findById(user2Id)
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + user2Id));

                    Chat chat = Chat.builder()
                            .participants(Set.of(user1, user2))
                            .build();
                    return chatRepository.save(chat);
                });
    }

}
