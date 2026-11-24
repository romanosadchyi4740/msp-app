package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessagePage;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomMessageResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.RoomChatSendRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomMessage;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomChatMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomChatServiceTest {

    @Mock
    RoomAccess roomAccess;

    @Mock
    RoomMessageRepository messageRepository;

    @Mock
    RoomChatMappingService mappingService;

    @Mock
    RoomChatNotifier notifier;

    @InjectMocks
    RoomChatService service;

    final UUID userId = UUID.randomUUID();
    final UUID roomId = UUID.randomUUID();

    @Test
    void send_OpenRoom_StoresTrimmedTextAndPublishesIt() {
        Room room = room(RoomStatus.OPEN);
        when(roomAccess.requireParticipant(userId, roomId)).thenReturn(participant(room));
        when(messageRepository.saveAndFlush(any(RoomMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        RoomMessageResponse response = new RoomMessageResponse().content("hello");
        when(mappingService.map(any(RoomMessage.class))).thenReturn(response);

        RoomMessageResponse sent = service.send(userId, roomId, new RoomChatSendRequest("  hello \n"));

        ArgumentCaptor<RoomMessage> saved = ArgumentCaptor.forClass(RoomMessage.class);
        verify(messageRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("hello");
        assertThat(saved.getValue().getAuthorId()).isEqualTo(userId);
        assertThat(saved.getValue().getRoom()).isSameAs(room);
        verify(notifier).publish(roomId, response);
        assertThat(sent).isSameAs(response);
    }

    @Test
    void send_MaxLength_IsAccepted() {
        when(roomAccess.requireParticipant(userId, roomId)).thenReturn(participant(room(RoomStatus.OPEN)));
        when(messageRepository.saveAndFlush(any(RoomMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.send(userId, roomId, new RoomChatSendRequest("a".repeat(2000)));

        verify(messageRepository).saveAndFlush(any(RoomMessage.class));
    }

    @Test
    void send_BlankOrMissingText_IsRefused() {
        for (RoomChatSendRequest request : new RoomChatSendRequest[]{
                null, new RoomChatSendRequest(null), new RoomChatSendRequest(" \t\n ")}) {
            assertThatThrownBy(() -> service.send(userId, roomId, request))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("A message needs some text");
        }
        verifyNoInteractions(roomAccess, messageRepository, notifier);
    }

    @Test
    void send_TooLong_IsRefused() {
        assertThatThrownBy(() -> service.send(userId, roomId, new RoomChatSendRequest("a".repeat(2001))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A message has at most 2000 characters");
        verifyNoInteractions(messageRepository, notifier);
    }

    @Test
    void send_ClosedRoom_IsRefused() {
        when(roomAccess.requireParticipant(userId, roomId)).thenReturn(participant(room(RoomStatus.CLOSED)));

        assertThatThrownBy(() -> service.send(userId, roomId, new RoomChatSendRequest("hi")))
                .isInstanceOf(RoomClosedException.class);
        verify(messageRepository, never()).saveAndFlush(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void send_NotAParticipant_IsNotFound() {
        when(roomAccess.requireParticipant(userId, roomId)).thenThrow(new RoomNotFoundException("Room not found"));

        assertThatThrownBy(() -> service.send(userId, roomId, new RoomChatSendRequest("hi")))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(messageRepository, notifier);
    }

    @Test
    void history_Participant_ReturnsTheMappedPage() {
        Page<RoomMessage> page = new PageImpl<>(List.of(RoomMessage.builder().build()));
        when(messageRepository.findPageByRoomId(roomId, PageRequest.of(2, 30))).thenReturn(page);
        RoomMessagePage mapped = new RoomMessagePage();
        when(mappingService.mapPage(page)).thenReturn(mapped);

        assertThat(service.history(userId, roomId, 2, 30)).isSameAs(mapped);
        verify(roomAccess).requireParticipant(userId, roomId);
    }

    @Test
    void history_NoPaging_UsesTheFirstPageOf50() {
        when(messageRepository.findPageByRoomId(any(), any(Pageable.class))).thenReturn(Page.empty());

        service.history(userId, roomId, null, null);

        verify(messageRepository).findPageByRoomId(roomId, PageRequest.of(0, 50));
    }

    @Test
    void history_BadPaging_IsRefused() {
        assertThatThrownBy(() -> service.history(userId, roomId, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.history(userId, roomId, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.history(userId, roomId, 0, 101))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A page holds 1 to 100 messages");
        verifyNoInteractions(messageRepository);
    }

    @Test
    void history_NotAParticipant_IsNotFound() {
        when(roomAccess.requireParticipant(userId, roomId)).thenThrow(new RoomNotFoundException("Room not found"));

        assertThatThrownBy(() -> service.history(userId, roomId, 0, 50)).isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(messageRepository);
    }

    private Room room(RoomStatus status) {
        return Room.builder().id(roomId).status(status).build();
    }

    private RoomParticipant participant(Room room) {
        return RoomParticipant.builder().room(room).userId(userId).build();
    }
}
