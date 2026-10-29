package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomCreateRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomListItem;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomPreview;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomFullException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantCount;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    InviteCodeGenerator inviteCodeGenerator;

    @Mock
    RoomMappingService roomMappingService;

    @Mock
    UserLookup userLookup;

    @Mock
    LessonService lessonService;

    @InjectMocks
    RoomService roomService;

    UUID hostId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    RoomResponse response = new RoomResponse();

    @BeforeEach
    void usersExist() {
        lenient().when(userLookup.exists(any())).thenReturn(true);
    }

    @Test
    void create_Valid_SavesTheRoomWithTheCreatorAsHost() {
        when(inviteCodeGenerator.generate()).thenReturn("Abc123Def456");
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> {
            Room room = invocation.getArgument(0);
            room.setId(roomId);
            return room;
        });
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        RoomResponse result = roomService.create(hostId, new RoomCreateRequest().name("  Guitar lesson  "));

        ArgumentCaptor<Room> room = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(room.capture());
        assertThat(room.getValue().getName()).isEqualTo("Guitar lesson");
        assertThat(room.getValue().getHostId()).isEqualTo(hostId);
        assertThat(room.getValue().getInviteCode()).isEqualTo("Abc123Def456");
        assertThat(room.getValue().getCapacity()).isEqualTo(5);
        assertThat(room.getValue().getStatus()).isEqualTo(RoomStatus.OPEN);
        ArgumentCaptor<RoomParticipant> host = ArgumentCaptor.forClass(RoomParticipant.class);
        verify(participantRepository).save(host.capture());
        assertThat(host.getValue().getUserId()).isEqualTo(hostId);
        assertThat(host.getValue().getRole()).isEqualTo(RoomRole.HOST);
        assertThat(host.getValue().getRoom()).isSameAs(room.getValue());
        assertThat(result).isSameAs(response);
    }

    @Test
    void create_BlankOrTooLongName_IsRejected() {
        for (String name : new String[]{null, "   ", "x".repeat(101)}) {
            assertThatThrownBy(() -> roomService.create(hostId, new RoomCreateRequest().name(name)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(roomRepository, participantRepository);
    }

    @Test
    void create_DeletedAccount_IsRejected() {
        when(userLookup.exists(hostId)).thenReturn(false);

        assertThatThrownBy(() -> roomService.create(hostId, new RoomCreateRequest().name("Lesson")))
                .isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(roomRepository, participantRepository);
    }

    @Test
    void create_TakenInviteCode_TriesAnotherOne() {
        when(inviteCodeGenerator.generate()).thenReturn("taken0000000", "free00000000");
        when(roomRepository.existsByInviteCode("taken0000000")).thenReturn(true);
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        roomService.create(hostId, new RoomCreateRequest().name("Lesson"));

        ArgumentCaptor<Room> room = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).save(room.capture());
        assertThat(room.getValue().getInviteCode()).isEqualTo("free00000000");
    }

    @Test
    void create_EveryInviteCodeTaken_GivesUp() {
        when(inviteCodeGenerator.generate()).thenReturn("taken0000000");
        when(roomRepository.existsByInviteCode("taken0000000")).thenReturn(true);

        assertThatThrownBy(() -> roomService.create(hostId, new RoomCreateRequest().name("Lesson")))
                .isInstanceOf(IllegalStateException.class);
        verify(inviteCodeGenerator, times(RoomService.MAX_INVITE_CODE_ATTEMPTS)).generate();
        verify(roomRepository, never()).save(any());
    }

    @Test
    void join_NewUserInOpenRoomWithAFreePlace_AddsAParticipantUnderTheLock() {
        Room room = room(RoomStatus.OPEN);
        when(roomRepository.findByInviteCodeForUpdate("code")).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());
        when(participantRepository.countByRoomId(roomId)).thenReturn(4L);
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        RoomResponse result = roomService.join(userId, "code");

        ArgumentCaptor<RoomParticipant> participant = ArgumentCaptor.forClass(RoomParticipant.class);
        InOrder order = inOrder(roomRepository, participantRepository);
        order.verify(roomRepository).findByInviteCodeForUpdate("code");
        order.verify(participantRepository).save(participant.capture());
        assertThat(participant.getValue().getUserId()).isEqualTo(userId);
        assertThat(participant.getValue().getRole()).isEqualTo(RoomRole.PARTICIPANT);
        assertThat(participant.getValue().getRoom()).isSameAs(room);
        verify(roomRepository, never()).findByInviteCode(any());
        assertThat(result).isSameAs(response);
    }

    @Test
    void join_FullRoom_IsRejectedWithRoomFull() {
        when(roomRepository.findByInviteCodeForUpdate("code")).thenReturn(Optional.of(room(RoomStatus.OPEN)));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());
        when(participantRepository.countByRoomId(roomId)).thenReturn(5L);

        assertThatThrownBy(() -> roomService.join(userId, "code"))
                .isInstanceOf(RoomFullException.class)
                .hasMessage("The room is full");
        verify(participantRepository, never()).save(any());
    }

    @Test
    void join_UserAlreadyInFullRoom_AddsNothingAndSucceeds() {
        Room room = room(RoomStatus.OPEN);
        when(roomRepository.findByInviteCodeForUpdate("code")).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(participant(room, userId, RoomRole.PARTICIPANT)));
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        assertThat(roomService.join(userId, "code")).isSameAs(response);
        verify(participantRepository, never()).countByRoomId(any());
        verify(participantRepository, never()).save(any());
    }

    @Test
    void join_ClosedRoom_IsRejected() {
        when(roomRepository.findByInviteCodeForUpdate("code")).thenReturn(Optional.of(room(RoomStatus.CLOSED)));

        assertThatThrownBy(() -> roomService.join(userId, "code")).isInstanceOf(RoomClosedException.class);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void join_DeletedAccount_IsRejectedBeforeLockingTheRoom() {
        when(userLookup.exists(userId)).thenReturn(false);

        assertThatThrownBy(() -> roomService.join(userId, "code")).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(roomRepository, participantRepository);
    }

    @Test
    void join_UnknownCode_IsNotFound() {
        when(roomRepository.findByInviteCodeForUpdate("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.join(userId, "nope")).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void regenerateInvite_Host_ReplacesTheCode() {
        Room room = room(RoomStatus.OPEN);
        when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(room, hostId, RoomRole.HOST)));
        when(inviteCodeGenerator.generate()).thenReturn("NewCode00000");
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        assertThat(roomService.regenerateInvite(hostId, roomId)).isSameAs(response);
        assertThat(room.getInviteCode()).isEqualTo("NewCode00000");
        verify(roomRepository).save(room);
    }

    @Test
    void regenerateInvite_Participant_IsForbidden() {
        Room room = room(RoomStatus.OPEN);
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(participant(room, userId, RoomRole.PARTICIPANT)));

        assertThatThrownBy(() -> roomService.regenerateInvite(userId, roomId))
                .isInstanceOf(UserNotAuthorizedException.class);
        assertThat(room.getInviteCode()).isEqualTo("OldCode00000");
        verifyNoInteractions(inviteCodeGenerator);
    }

    @Test
    void regenerateInvite_ClosedRoom_IsRejected() {
        Room room = room(RoomStatus.CLOSED);
        when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(room, hostId, RoomRole.HOST)));

        assertThatThrownBy(() -> roomService.regenerateInvite(hostId, roomId))
                .isInstanceOf(RoomClosedException.class);
        verifyNoInteractions(inviteCodeGenerator);
    }

    @Test
    void regenerateInvite_NotAParticipant_IsNotFound() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.regenerateInvite(userId, roomId))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void close_Host_ClosesUnderTheLockAndEndsTheLesson() {
        Room room = room(RoomStatus.OPEN);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(room, hostId, RoomRole.HOST)));
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        assertThat(roomService.close(hostId, roomId)).isSameAs(response);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.CLOSED);
        verify(roomRepository).save(room);
        verify(lessonService).endActiveLesson(roomId, LessonEndReason.ROOM_CLOSED);
    }

    @Test
    void close_AlreadyClosed_ChangesNothing() {
        Room room = room(RoomStatus.CLOSED);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(room, hostId, RoomRole.HOST)));
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        assertThat(roomService.close(hostId, roomId)).isSameAs(response);
        verify(roomRepository, never()).save(any());
        verifyNoInteractions(lessonService);
    }

    @Test
    void close_Participant_IsForbidden() {
        Room room = room(RoomStatus.OPEN);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(participant(room, userId, RoomRole.PARTICIPANT)));

        assertThatThrownBy(() -> roomService.close(userId, roomId)).isInstanceOf(UserNotAuthorizedException.class);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
        verifyNoInteractions(lessonService);
    }

    @Test
    void close_NotAParticipantOrNoRoom_IsNotFound() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room(RoomStatus.OPEN)));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());
        UUID missingRoomId = UUID.randomUUID();
        when(roomRepository.findByIdForUpdate(missingRoomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.close(userId, roomId)).isInstanceOf(RoomNotFoundException.class);
        assertThatThrownBy(() -> roomService.close(hostId, missingRoomId)).isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(lessonService);
    }

    @Test
    void getRoom_Participant_MapsTheRoomWithItsParticipants() {
        Room room = room(RoomStatus.OPEN);
        RoomParticipant participant = participant(room, userId, RoomRole.PARTICIPANT);
        List<RoomParticipant> participants = List.of(participant(room, hostId, RoomRole.HOST), participant);
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.of(participant));
        when(participantRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId)).thenReturn(participants);
        when(roomMappingService.map(room, participants, userId)).thenReturn(response);

        assertThat(roomService.getRoom(userId, roomId)).isSameAs(response);
    }

    @Test
    void getRoom_NotAParticipant_IsNotFound() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getRoom(userId, roomId)).isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(roomMappingService);
    }

    @Test
    void getPreview_KnownCode_MapsTheRoomWithItsParticipantCount() {
        Room room = room(RoomStatus.OPEN);
        RoomPreview preview = new RoomPreview();
        when(roomRepository.findByInviteCode("code")).thenReturn(Optional.of(room));
        when(participantRepository.countByRoomId(roomId)).thenReturn(3L);
        when(roomMappingService.mapPreview(room, 3L)).thenReturn(preview);

        assertThat(roomService.getPreview("code")).isSameAs(preview);
    }

    @Test
    void getPreview_UnknownCode_IsNotFound() {
        when(roomRepository.findByInviteCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.getPreview("nope")).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void getMyRooms_Memberships_CountsParticipantsInOneQuery() {
        Room room = room(RoomStatus.OPEN);
        List<RoomParticipant> memberships = List.of(participant(room, userId, RoomRole.PARTICIPANT));
        RoomParticipantCount count = mock(RoomParticipantCount.class);
        when(count.getRoomId()).thenReturn(roomId);
        when(count.getCount()).thenReturn(2L);
        List<RoomListItem> items = List.of(new RoomListItem());
        when(participantRepository.findAllWithRoomByUserId(userId)).thenReturn(memberships);
        when(participantRepository.countByRoomIds(List.of(roomId))).thenReturn(List.of(count));
        when(roomMappingService.mapAll(memberships, Map.of(roomId, 2L))).thenReturn(items);

        assertThat(roomService.getMyRooms(userId)).isSameAs(items);
    }

    @Test
    void getMyRooms_NoMemberships_IsEmptyWithoutCounting() {
        when(participantRepository.findAllWithRoomByUserId(userId)).thenReturn(List.of());

        assertThat(roomService.getMyRooms(userId)).isEmpty();
        verify(participantRepository, never()).countByRoomIds(any());
    }

    private Room room(RoomStatus status) {
        return Room.builder().id(roomId).hostId(hostId).name("Lesson").inviteCode("OldCode00000").status(status)
                .build();
    }

    private static RoomParticipant participant(Room room, UUID userId, RoomRole role) {
        return RoomParticipant.builder().id(UUID.randomUUID()).room(room).userId(userId).role(role).build();
    }
}
