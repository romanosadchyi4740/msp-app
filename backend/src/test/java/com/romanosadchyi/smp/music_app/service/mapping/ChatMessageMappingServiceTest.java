package com.romanosadchyi.smp.music_app.service.mapping;

import com.romanosadchyi.smp.music_app.model.dto.response.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.model.entity.Chat;
import com.romanosadchyi.smp.music_app.model.entity.Message;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.music.internal.entity.Track;
import com.romanosadchyi.smp.music_app.music.internal.mapping.TrackMappingService;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
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
    UserLookup userLookup;

    @Mock
    TrackMappingService trackMappingService;

    @InjectMocks
    ChatMessageMappingService chatMessageMappingService;

    @Test
    void map_FullMessage_ReturnsDto() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Chat chat = Chat.builder().id(chatId).build();
        UUID senderId = UUID.randomUUID();
        Track sharedTrack = Track.builder().id(UUID.randomUUID()).build();
        LocalDateTime now = LocalDateTime.now();

        Message message = Message.builder()
                .id(messageId)
                .chat(chat)
                .senderId(senderId)
                .content("Hello")
                .timestamp(now)
                .sharedTrack(sharedTrack)
                .sharedTrackTimestamp(10)
                .fileUrl("url")
                .build();

        UserResponseDto senderDto = UserResponseDto.builder().id(senderId).build();
        TrackResponseDto trackDto = TrackResponseDto.builder().id(sharedTrack.getId()).build();

        when(userLookup.getProfile(senderId)).thenReturn(senderDto);
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
        UUID senderId = UUID.randomUUID();

        Message message = Message.builder()
                .id(messageId)
                .chat(chat)
                .senderId(senderId)
                .build();

        when(userLookup.getProfile(senderId)).thenReturn(UserResponseDto.builder().id(senderId).build());

        ChatMessageResponseDto result = chatMessageMappingService.map(message);

        assertNotNull(result);
        assertEquals(messageId, result.id());
    }
}
