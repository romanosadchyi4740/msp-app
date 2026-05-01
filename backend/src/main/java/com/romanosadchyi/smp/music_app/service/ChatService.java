package com.romanosadchyi.smp.music_app.service;

import com.romanosadchyi.smp.music_app.exception.ChatNotFoundException;
import com.romanosadchyi.smp.music_app.exception.TrackNotFoundException;
import com.romanosadchyi.smp.music_app.exception.UserNotFoundException;
import com.romanosadchyi.smp.music_app.model.dto.request.ChatMessageRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.request.ClusterCreateRequestDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.*;
import com.romanosadchyi.smp.music_app.repository.ChatMembershipRepository;
import com.romanosadchyi.smp.music_app.repository.ChatRepository;
import com.romanosadchyi.smp.music_app.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.repository.TrackRepository;
import com.romanosadchyi.smp.music_app.repository.UserRepository;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMappingService;
import com.romanosadchyi.smp.music_app.service.mapping.ChatMessageMappingService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatService {

    ChatRepository chatRepository;
    ChatMembershipRepository chatMembershipRepository;
    MessageRepository messageRepository;
    UserRepository userRepository;
    TrackRepository trackRepository;
    ChatMessageMappingService mappingService;
    ChatMappingService chatMappingService;
    SimpMessagingTemplate messagingTemplate;

    @Transactional
    public void processMessage(ChatMessageRequestDto request, UUID senderId) {
        ChatMessageResponseDto savedMessage = saveMessage(request, senderId);
        log.info("Message saved with ID: {} in chat: {}", savedMessage.id(), savedMessage.chatId());

        Chat chat = chatRepository.findById(savedMessage.chatId())
                .orElseThrow(() -> new IllegalArgumentException("Chat not found"));

        chat.getMemberships().forEach(m ->
                messagingTemplate.convertAndSendToUser(
                        m.getUser().getId().toString(),
                        "/queue/messages",
                        savedMessage
                )
        );
    }

    @Transactional(readOnly = true)
    public Page<ChatMessageResponseDto> getMessageHistory(UUID chatId, Pageable pageable) {
        return messageRepository.findByChatIdOrderByTimestampDesc(chatId, pageable)
                .map(mappingService::map);
    }

    @Transactional
    public ChatResponseDto getOrCreateChatDto(UUID user1Id, UUID user2Id) {
        return chatMappingService.map(getOrCreateChat(user1Id, user2Id), user1Id);
    }

    @Transactional
    public ChatResponseDto acceptChat(UUID chatId, UUID userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

        ChatMembership membership = chat.getMemberships().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User is not a participant of this chat"));

        if (membership.isAdmin()) {
            throw new IllegalArgumentException("Initiator cannot accept their own chat");
        }

        membership.setStatus(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.ACCEPTED);
        chatMembershipRepository.save(membership);
        return chatMappingService.map(chat, userId);
    }

    @Transactional
    public void rejectChat(UUID chatId, UUID userId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new ChatNotFoundException("Chat not found"));

        ChatMembership membership = chat.getMemberships().stream()
                .filter(m -> m.getUser().getId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User is not a participant of this chat"));

        membership.setStatus(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.REJECTED);
        chatMembershipRepository.save(membership);
    }

    @Transactional
    public ChatResponseDto createCluster(UUID creatorId, ClusterCreateRequestDto request) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new UserNotFoundException("User not found: " + creatorId));

        Chat chat = Chat.builder()
                .isGroup(true)
                .name(request.groupName())
                .build();
        
        chat = chatRepository.save(chat);

        ChatMembership creatorMembership = ChatMembership.builder()
                .chat(chat)
                .user(creator)
                .status(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.ACCEPTED)
                .isAdmin(true)
                .build();
        chat.getMemberships().add(creatorMembership);

        for (UUID participantId : request.participantIds()) {
            if (participantId.equals(creatorId)) continue;
            User participant = userRepository.findById(participantId)
                    .orElseThrow(() -> new UserNotFoundException("User not found: " + participantId));
            ChatMembership participantMembership = ChatMembership.builder()
                    .chat(chat)
                    .user(participant)
                    .status(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.PENDING)
                    .isAdmin(false)
                    .build();
            chat.getMemberships().add(participantMembership);
        }

        chatMembershipRepository.saveAll(chat.getMemberships());
        return chatMappingService.map(chat, creatorId);
    }

    @Transactional(readOnly = true)
    public List<ChatResponseDto> getUserChats(UUID userId) {
        return chatRepository.findAllByParticipantId(userId).stream()
                .map(chat -> chatMappingService.map(chat, userId))
                .collect(Collectors.toList());
    }

    private ChatMessageResponseDto saveMessage(ChatMessageRequestDto request, UUID senderId) {
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

        ChatMembership senderMembership = chat.getMemberships().stream()
                .filter(m -> m.getUser().getId().equals(senderId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("User not in chat"));

        if (senderMembership.getStatus() == com.romanosadchyi.smp.music_app.model.enums.ChatStatus.REJECTED) {
            senderMembership.setStatus(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.PENDING);
            senderMembership.setAdmin(true);
            chatMembershipRepository.save(senderMembership);
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
                .sharedTrackTimestamp(request.sharedTrackTimestamp())
                .fileUrl(request.fileUrl())
                .timestamp(LocalDateTime.now())
                .build();

        return mappingService.map(messageRepository.save(message));
    }

    private Chat getOrCreateChat(UUID user1Id, UUID user2Id) {
        return chatRepository.findChatBetweenUsers(user1Id, user2Id)
                .map(chat -> {
                    ChatMembership m1 = chat.getMemberships().stream().filter(m -> m.getUser().getId().equals(user1Id)).findFirst().orElse(null);
                    ChatMembership m2 = chat.getMemberships().stream().filter(m -> m.getUser().getId().equals(user2Id)).findFirst().orElse(null);
                    
                    if (m1 != null && m1.getStatus() == com.romanosadchyi.smp.music_app.model.enums.ChatStatus.REJECTED) {
                        m1.setStatus(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.ACCEPTED);
                        m1.setAdmin(true);
                        chatMembershipRepository.save(m1);
                    }
                    if (m2 != null && m2.getStatus() == com.romanosadchyi.smp.music_app.model.enums.ChatStatus.REJECTED) {
                        m2.setStatus(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.PENDING);
                        m2.setAdmin(false);
                        chatMembershipRepository.save(m2);
                    }
                    return chat;
                })
                .orElseGet(() -> {
                    User user1 = userRepository.findById(user1Id)
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + user1Id));
                    User user2 = userRepository.findById(user2Id)
                            .orElseThrow(() -> new UserNotFoundException("User not found: " + user2Id));

                    Chat chat = Chat.builder()
                            .isGroup(false)
                            .build();
                    chat = chatRepository.save(chat);

                    ChatMembership membership1 = ChatMembership.builder()
                            .chat(chat)
                            .user(user1)
                            .status(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.ACCEPTED)
                            .isAdmin(true)
                            .build();
                    ChatMembership membership2 = ChatMembership.builder()
                            .chat(chat)
                            .user(user2)
                            .status(com.romanosadchyi.smp.music_app.model.enums.ChatStatus.PENDING)
                            .isAdmin(false)
                            .build();

                    chat.getMemberships().add(membership1);
                    chat.getMemberships().add(membership2);
                    chatMembershipRepository.saveAll(chat.getMemberships());

                    return chat;
                });
    }

}