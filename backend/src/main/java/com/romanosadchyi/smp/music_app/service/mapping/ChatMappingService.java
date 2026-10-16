package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.ChatMembership;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMappingService {

    UserLookup userLookup;
    ChatMessageMappingService messageMappingService;

    public ChatResponseDto map(Chat chat, UUID currentUserId) {
        ChatMembership currentUserMembership = chat.getMemberships().stream()
                .filter(m -> m.getUserId().equals(currentUserId))
                .findFirst()
                .orElse(null);

        UUID initiatorId = chat.getMemberships().stream()
                .filter(ChatMembership::isAdmin)
                .map(m -> m.getUserId())
                .findFirst()
                .orElse(null);

        return ChatResponseDto.builder()
                .id(chat.getId())
                .isGroup(chat.getIsGroup() != null && chat.getIsGroup())
                .name(chat.getName())
                .participants(chat.getMemberships().stream()
                        .map(m -> userLookup.getProfile(m.getUserId()))
                        .collect(Collectors.toList()))
                .lastMessage(chat.getMessages().stream()
                        .max(Comparator.comparing(Message::getTimestamp))
                        .map(messageMappingService::map)
                        .orElse(null))
                .initiatorId(initiatorId)
                .status(currentUserMembership != null ? currentUserMembership.getStatus().name() : "PENDING")
                .build();
    }
}
