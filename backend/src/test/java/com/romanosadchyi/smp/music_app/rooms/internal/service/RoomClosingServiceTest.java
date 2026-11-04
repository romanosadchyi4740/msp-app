package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * With the real {@link RoomAccess} built from the mocks, so the host check runs as in production.
 */
@ExtendWith(MockitoExtension.class)
class RoomClosingServiceTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    RoomMappingService roomMappingService;

    @Mock
    UserLookup userLookup;

    @Mock
    LessonLifecycle lessonLifecycle;

    RoomClosingService roomClosingService;

    UUID hostId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    RoomResponse response = new RoomResponse();

    @BeforeEach
    void setUp() {
        lenient().when(userLookup.exists(any())).thenReturn(true);
        RoomAccess roomAccess = new RoomAccess(roomRepository, participantRepository, userLookup);
        roomClosingService = new RoomClosingService(roomRepository, participantRepository, roomAccess, lessonLifecycle,
                roomMappingService);
    }

    @Test
    void close_Host_ClosesUnderTheLockAndEndsTheLesson() {
        Room room = room(RoomStatus.OPEN);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(room, hostId, RoomRole.HOST)));
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        assertThat(roomClosingService.close(hostId, roomId)).isSameAs(response);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.CLOSED);
        verify(roomRepository).save(room);
        verify(lessonLifecycle).endActive(roomId, LessonEndReason.ROOM_CLOSED);
    }

    @Test
    void close_AlreadyClosed_ChangesNothing() {
        Room room = room(RoomStatus.CLOSED);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(room, hostId, RoomRole.HOST)));
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        assertThat(roomClosingService.close(hostId, roomId)).isSameAs(response);
        verify(roomRepository, never()).save(any());
        verifyNoInteractions(lessonLifecycle);
    }

    @Test
    void close_Participant_IsForbidden() {
        Room room = room(RoomStatus.OPEN);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(participant(room, userId, RoomRole.PARTICIPANT)));

        assertThatThrownBy(() -> roomClosingService.close(userId, roomId)).isInstanceOf(UserNotAuthorizedException.class);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
        verifyNoInteractions(lessonLifecycle);
    }

    @Test
    void close_NotAParticipantOrNoRoom_IsNotFound() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room(RoomStatus.OPEN)));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());
        UUID missingRoomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(missingRoomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomClosingService.close(userId, roomId)).isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> roomClosingService.close(hostId, missingRoomId)).isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(lessonLifecycle);
    }

    private Room room(RoomStatus status) {
        return Room.builder().id(roomId).hostId(hostId).name("Lesson").inviteCode("OldCode00000").status(status)
                .build();
    }

    private static RoomParticipant participant(Room room, UUID userId, RoomRole role) {
        return RoomParticipant.builder().id(UUID.randomUUID()).room(room).userId(userId).role(role).build();
    }
}
