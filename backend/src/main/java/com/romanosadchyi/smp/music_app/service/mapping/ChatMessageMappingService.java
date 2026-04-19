package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageMappingService {

    UserMappingService userMappingService;
    TrackMappingService trackMappingService;

    public ChatMessageResponseDto map(Message message) {
        return ChatMessageResponseDto.builder()
                .id(message.getId())
                .chatId(message.getChat().getId())
                .sender(userMappingService.map(message.getSender()))
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .sharedTrack(message.getSharedTrack() != null ? trackMappingService.map(message.getSharedTrack()) : null)
                .build();
    }
}
