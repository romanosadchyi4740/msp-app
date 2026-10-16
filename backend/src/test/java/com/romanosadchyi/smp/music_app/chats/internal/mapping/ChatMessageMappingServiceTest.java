package com.romanosadchyi.smp.music_app.chats.internal.mapping;

import com.romanosadchyi.smp.music_app.chats.internal.dto.ChatMessageResponseDto;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Chat;
import com.romanosadchyi.smp.music_app.chats.internal.entity.Message;
import com.romanosadchyi.smp.music_app.music.external.TrackLookup;
import com.romanosadchyi.smp.music_app.music.external.TrackResponseDto;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserResponseDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageMappingServiceTest {

    @Mock
    UserLookup userLookup;

    @Mock
    TrackLookup trackLookup;

    @InjectMocks
    ChatMessageMappingService chatMessageMappingService;

    @Test
    void map_FullMessage_ReturnsDto() {
        UUID messageId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        Chat chat = Chat.builder().id(chatId).build();
        UUID senderId = UUID.randomUUID();
        UUID sharedTrackId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();

        Message message = Message.builder()
                .id(messageId)
                .chat(chat)
                .senderId(senderId)
                .content("Hello")
                .timestamp(now)
                .sharedTrackId(sharedTrackId)
                .sharedTrackTimestamp(10)
                .fileUrl("url")
                .build();

        UserResponseDto senderDto = UserResponseDto.builder().id(senderId).build();
        TrackResponseDto trackDto = TrackResponseDto.builder().id(sharedTrackId).build();

        when(userLookup.getProfile(senderId)).thenReturn(senderDto);
        when(trackLookup.findTracks(List.of(sharedTrackId))).thenReturn(List.of(trackDto));

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
        assertNull(result.sharedTrack());
        verifyNoInteractions(trackLookup);
    }

    @Test
    void map_SharedTrackDeleted_HasNoTrack() {
        UUID senderId = UUID.randomUUID();
        UUID trackId = UUID.randomUUID();
        Message message = Message.builder().id(UUID.randomUUID()).chat(Chat.builder().id(UUID.randomUUID()).build())
                .senderId(senderId).sharedTrackId(trackId).build();
        when(userLookup.getProfile(senderId)).thenReturn(UserResponseDto.builder().id(senderId).build());
        when(trackLookup.findTracks(List.of(trackId))).thenReturn(List.of());

        assertNull(chatMessageMappingService.map(message).sharedTrack());
    }

    @Test
    void mapPage_LooksUpSharedTracksOnceForThePage() {
        UUID senderId = UUID.randomUUID();
        UUID shared = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Chat chat = Chat.builder().id(UUID.randomUUID()).build();
        List<Message> messages = List.of(
                Message.builder().id(UUID.randomUUID()).chat(chat).senderId(senderId).sharedTrackId(shared).build(),
                Message.builder().id(UUID.randomUUID()).chat(chat).senderId(senderId).build(),
                Message.builder().id(UUID.randomUUID()).chat(chat).senderId(senderId).sharedTrackId(other).build(),
                Message.builder().id(UUID.randomUUID()).chat(chat).senderId(senderId).sharedTrackId(shared).build());
        TrackResponseDto sharedDto = TrackResponseDto.builder().id(shared).build();
        TrackResponseDto otherDto = TrackResponseDto.builder().id(other).build();
        when(userLookup.getProfile(senderId)).thenReturn(UserResponseDto.builder().id(senderId).build());
        when(trackLookup.findTracks(List.of(shared, other))).thenReturn(List.of(sharedDto, otherDto));

        Page<ChatMessageResponseDto> result = chatMessageMappingService.mapPage(new PageImpl<>(messages));

        assertThat(result.getContent()).extracting(ChatMessageResponseDto::sharedTrack)
                .containsExactly(sharedDto, null, otherDto, sharedDto);
        verify(trackLookup).findTracks(anyCollection());
    }
}
