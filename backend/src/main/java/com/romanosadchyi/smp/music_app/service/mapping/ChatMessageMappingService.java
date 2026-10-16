package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChatMessageMappingService {

    UserLookup userLookup;
    TrackMappingService trackMappingService;

    public ChatMessageResponseDto map(Message message) {
        return ChatMessageResponseDto.builder()
                .id(message.getId())
                .chatId(message.getChat().getId())
                .sender(userLookup.getProfile(message.getSenderId()))
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .sharedTrack(message.getSharedTrack() != null ? trackMappingService.map(message.getSharedTrack()) : null)
                .sharedTrackTimestamp(message.getSharedTrackTimestamp())
                .fileUrl(message.getFileUrl())
                .build();
    }
}
