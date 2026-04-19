package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMappingService {

    UserMappingService userMappingService;
    ChatMessageMappingService messageMappingService;

    public ChatResponseDto map(Chat chat) {
        return ChatResponseDto.builder()
                .id(chat.getId())
                .participants(chat.getParticipants().stream()
                        .map(userMappingService::map)
                        .collect(Collectors.toList()))
                .lastMessage(chat.getMessages().stream()
                        .max(Comparator.comparing(Message::getTimestamp))
                        .map(messageMappingService::map)
                        .orElse(null))
                .build();
    }
}
