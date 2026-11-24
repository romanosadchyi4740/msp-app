package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.chats.internal.repository.MessageRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps chats to responses for one user. {@link #mapAll} loads only the last message of each chat, in one query, and
 * maps them together, so their senders and shared tracks are looked up once. It reads the messages through the
 * repository rather than {@code Chat.messages}, which would load and decrypt every message of every chat.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMappingService {

    UserLookup userLookup;
    ChatMessageMappingService messageMappingService;
    MessageRepository messageRepository;

    public ChatResponseDto map(Chat chat, UUID currentUserId) {
        return mapAll(List.of(chat), currentUserId).getFirst();
    }

    public List<ChatResponseDto> mapAll(Collection<Chat> chats, UUID currentUserId) {
        Map<UUID, ChatMessageResponseDto> lastMessages = lastMessagesOf(chats);
        Map<UUID, UserResponseDto> participants = participantsOf(chats);
        return chats.stream()
                .map(chat -> map(chat, currentUserId, lastMessages.get(chat.getId()), participants))
                .toList();
    }

    private Map<UUID, UserResponseDto> participantsOf(Collection<Chat> chats) {
        return userLookup.findProfiles(chats.stream()
                .flatMap(chat -> chat.getMemberships().stream())
                .map(ChatMembership::getUserId)
                .distinct()
                .toList());
    }

    /**
     * The last message of each chat that has messages, by chat id.
     */
    private Map<UUID, ChatMessageResponseDto> lastMessagesOf(Collection<Chat> chats) {
        List<UUID> chatIds = chats.stream().map(Chat::getId).filter(Objects::nonNull).toList();
        if (chatIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Message> latest = new LinkedHashMap<>();
        messageRepository.findLatestByChatIds(chatIds)
                .forEach(message -> latest.putIfAbsent(message.getChat().getId(), message));
        if (latest.isEmpty()) {
            return Map.of();
        }
        List<UUID> chatsWithMessages = List.copyOf(latest.keySet());
        List<ChatMessageResponseDto> mapped = messageMappingService.mapAll(List.copyOf(latest.values()));
        Map<UUID, ChatMessageResponseDto> byChat = new HashMap<>();
        for (int i = 0; i < chatsWithMessages.size(); i++) {
            byChat.put(chatsWithMessages.get(i), mapped.get(i));
        }
        return byChat;
    }

    private ChatResponseDto map(Chat chat, UUID currentUserId, ChatMessageResponseDto lastMessage,
                                Map<UUID, UserResponseDto> participants) {
        ChatMembership currentUserMembership = chat.getMemberships().stream()
                .filter(m -> m.getUserId().equals(currentUserId))
                .findFirst()
                .orElse(null);

        UUID initiatorId = chat.getMemberships().stream()
                .filter(ChatMembership::isAdmin)
                .map(ChatMembership::getUserId)
                .findFirst()
                .orElse(null);

        return ChatResponseDto.builder()
                .id(chat.getId())
                .isGroup(chat.getIsGroup() != null && chat.getIsGroup())
                .name(chat.getName())
                .participants(chat.getMemberships().stream()
                        .map(m -> participants.get(m.getUserId()))
                        .collect(Collectors.toList()))
                .lastMessage(lastMessage)
                .initiatorId(initiatorId)
                .status(currentUserMembership != null ? currentUserMembership.getStatus().name() : "PENDING")
                .build();
    }
}
