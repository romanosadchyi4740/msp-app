package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.TrackResponseDto;
import com.romanosadchyi.smp.music_app.model.dto.response.UserResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import com.romanosadchyi.smp.music_app.model.entity.Track;
import com.romanosadchyi.smp.music_app.model.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageMappingServiceTest {

    @Mock
    UserMappingService userMappingService;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    ChatMessageMappingService chatMessageMappingService;

    @Test
    void map_FullMessage_ReturnsDto() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Chat chat = Chat.builder().id(chatId).build();
        User sender = User.builder().id(UUID.randomUUID()).build();
        Track sharedTrack = Track.builder().id(UUID.randomUUID()).build();
        LocalDateTime now = LocalDateTime.now();

        Message message = Message.builder()
                .id(messageId)
                .chat(chat)
                .sender(sender)
                .content("Hello")
                .timestamp(now)
                .sharedTrack(sharedTrack)
                .sharedTrackTimestamp(10)
                .fileUrl("url")
                .build();

        UserResponseDto senderDto = UserResponseDto.builder().id(sender.getId()).build();
        TrackResponseDto trackDto = TrackResponseDto.builder().id(sharedTrack.getId()).build();

        when(userMappingService.map(sender)).thenReturn(senderDto);
        when(trackMappingService.map(sharedTrack)).thenReturn(trackDto);

        ChatMessageResponseDto result = chatMessageMappingService.map(message);

        assertNotNull(result);
        assertEquals(messageId, result.id());
        assertEquals(chatId, result.chatId());
        assertEquals(senderDto, result.sender());
        assertEquals("Hello", result.content());
        assertEquals(now, result.timestamp());
        assertEquals(trackDto, result.sharedTrack());
        assertEquals(10, result.sharedTrackTimestamp());
        assertEquals("url", result.fileUrl());
    }

    @Test
    void map_MessageWithoutTrack_ReturnsDto() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Chat chat = Chat.builder().id(chatId).build();
        User sender = User.builder().id(UUID.randomUUID()).build();

        Message message = Message.builder()
                .id(messageId)
                .chat(chat)
                .sender(sender)
                .build();

        when(userMappingService.map(sender)).thenReturn(UserResponseDto.builder().id(sender.getId()).build());

        ChatMessageResponseDto result = chatMessageMappingService.map(message);

        assertNotNull(result);
        assertEquals(messageId, result.id());
    }
}
