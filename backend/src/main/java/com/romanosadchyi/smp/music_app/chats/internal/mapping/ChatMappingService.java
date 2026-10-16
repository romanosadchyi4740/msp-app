package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Maps chats to responses for one user. {@link #mapAll} maps the last messages of all chats together, so their
 * shared tracks are looked up once.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMappingService {

    UserLookup userLookup;
    ChatMessageMappingService messageMappingService;

    public ChatResponseDto map(Chat chat, UUID currentUserId) {
        return mapAll(List.of(chat), currentUserId).getFirst();
    }

    public List<ChatResponseDto> mapAll(Collection<Chat> chats, UUID currentUserId) {
        Map<Chat, ChatMessageResponseDto> lastMessages = lastMessagesOf(chats);
        return chats.stream()
                .map(chat -> map(chat, currentUserId, lastMessages.get(chat)))
                .toList();
    }

    /**
     * The last message of each chat that has messages.
     */
    private Map<Chat, ChatMessageResponseDto> lastMessagesOf(Collection<Chat> chats) {
        List<Chat> chatsWithMessages = new ArrayList<>();
        List<Message> lastMessages = new ArrayList<>();
        for (Chat chat : chats) {
            chat.getMessages().stream()
                    .max(Comparator.comparing(Message::getTimestamp))
                    .ifPresent(message -> {
                        chatsWithMessages.add(chat);
                        lastMessages.add(message);
                    });
        }
        List<ChatMessageResponseDto> mapped = messageMappingService.mapAll(lastMessages);
        Map<Chat, ChatMessageResponseDto> byChat = new IdentityHashMap<>();
        for (int i = 0; i < chatsWithMessages.size(); i++) {
            byChat.put(chatsWithMessages.get(i), mapped.get(i));
        }
        return byChat;
    }

    private ChatResponseDto map(Chat chat, UUID currentUserId, ChatMessageResponseDto lastMessage) {
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
                        .map(m -> userLookup.getProfile(m.getUserId()))
                        .collect(Collectors.toList()))
                .lastMessage(lastMessage)
                .initiatorId(initiatorId)
                .status(currentUserMembership != null ? currentUserMembership.getStatus().name() : "PENDING")
                .build();
    }
}
