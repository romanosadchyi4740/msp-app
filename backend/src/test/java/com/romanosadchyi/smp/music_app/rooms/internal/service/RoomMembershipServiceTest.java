package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.HostTransferRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomParticipantNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomMembershipServiceTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    RoomAccess roomAccess;

    @Mock
    HostHandover hostHandover;

    @Mock
    LessonLifecycle lessonLifecycle;

    @Mock
    ParticipantNotifier participantNotifier;

    @Mock
    RoomSubscriptionTracker roomSubscriptionTracker;

    @Mock
    RoomMappingService roomMappingService;

    @InjectMocks
    RoomMembershipService roomMembershipService;

    UUID roomId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    Room room = Room.builder().id(roomId).hostId(hostId).status(RoomStatus.OPEN).build();
    RoomParticipant host = RoomParticipant.builder().room(room).userId(hostId).role(RoomRole.HOST).build();
    RoomParticipant student = RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT)
            .build();

    // transferHost

    @Test
    void transferHost_ToAParticipant_HandsOverWithoutPausing() {
        RoomResponse response = new RoomResponse();
        lockRoomAsHost();
        when(participantRepository.findByRoomIdAndUserId(roomId, studentId)).thenReturn(Optional.of(student));
        when(participantRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId)).thenReturn(List.of(host, student));
        when(roomMappingService.map(room, List.of(host, student), hostId)).thenReturn(response);

        assertThat(roomMembershipService.transferHost(hostId, roomId, new HostTransferRequest().userId(studentId)))
                .isSameAs(response);

        verify(hostHandover).transfer(room, host, student, false);
    }

    @Test
    void transferHost_WithoutNewHost_IsABadRequest() {
        assertThatThrownBy(() -> roomMembershipService.transferHost(hostId, roomId, new HostTransferRequest()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roomMembershipService.transferHost(hostId, roomId, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomAccess, hostHandover);
    }

    @Test
    void transferHost_ByParticipant_IsForbidden() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomAccess.requireHost(eq(studentId), eq(roomId), anyString()))
                .thenThrow(new UserNotAuthorizedException("Only the host can hand over the room"));

        assertThatThrownBy(() -> roomMembershipService.transferHost(studentId, roomId,
                new HostTransferRequest().userId(hostId)))
                .isInstanceOf(UserNotAuthorizedException.class);
        verifyNoInteractions(hostHandover);
    }

    @Test
    void transferHost_ClosedRoom_IsAConflict() {
        room.setStatus(RoomStatus.CLOSED);
        lockRoomAsHost();

        assertThatThrownBy(() -> roomMembershipService.transferHost(hostId, roomId,
                new HostTransferRequest().userId(studentId)))
                .isInstanceOf(RoomClosedException.class);
        verifyNoInteractions(hostHandover);
    }

    @Test
    void transferHost_ToThemselves_IsABadRequest() {
        lockRoomAsHost();

        assertThatThrownBy(() -> roomMembershipService.transferHost(hostId, roomId,
                new HostTransferRequest().userId(hostId)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hostHandover);
    }

    @Test
    void transferHost_ToSomeoneNotInTheRoom_IsNotFound() {
        UUID outsiderId = UUID.randomUUID();
        lockRoomAsHost();
        when(participantRepository.findByRoomIdAndUserId(roomId, outsiderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomMembershipService.transferHost(hostId, roomId,
                new HostTransferRequest().userId(outsiderId)))
                .isInstanceOf(RoomParticipantNotFoundException.class);
        verifyNoInteractions(hostHandover);
    }

    // leave

    @Test
    void leave_Participant_FreesThePlaceTellsTheRoomAndEndsTheSubscriptions() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomAccess.requireParticipant(studentId, roomId)).thenReturn(student);

        roomMembershipService.leave(studentId, roomId);

        InOrder order = inOrder(roomRepository, participantRepository, participantNotifier, roomSubscriptionTracker);
        order.verify(roomRepository).findByIdForUpdate(roomId);
        order.verify(participantRepository).delete(student);
        order.verify(participantNotifier).publish(roomId,
                new ParticipantEvent(ParticipantEventType.LEFT, studentId, hostId));
        order.verify(roomSubscriptionTracker).revoke(roomId, studentId);
        verifyNoInteractions(hostHandover, lessonLifecycle);
    }

    @Test
    void leave_HostWithOthers_HandsOverAndPausesPlayback() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomAccess.requireParticipant(hostId, roomId)).thenReturn(host);
        when(hostHandover.successor(roomId, hostId)).thenReturn(Optional.of(student));

        roomMembershipService.leave(hostId, roomId);

        InOrder order = inOrder(hostHandover, participantRepository);
        order.verify(hostHandover).transfer(room, host, student, true);
        order.verify(participantRepository).delete(host);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
        verifyNoInteractions(lessonLifecycle);
    }

    @Test
    void leave_LastOneInTheRoom_ClosesItAndEndsTheLesson() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomAccess.requireParticipant(hostId, roomId)).thenReturn(host);
        when(hostHandover.successor(roomId, hostId)).thenReturn(Optional.empty());

        roomMembershipService.leave(hostId, roomId);

        assertThat(room.getStatus()).isEqualTo(RoomStatus.CLOSED);
        verify(roomRepository).save(room);
        verify(lessonLifecycle).endActive(roomId, LessonEndReason.ROOM_CLOSED);
        verify(participantRepository).delete(host);
        verify(hostHandover, never()).transfer(any(), any(), any(), anyBoolean());
    }

    @Test
    void leave_HostOfAClosedRoom_HandsNothingOver() {
        room.setStatus(RoomStatus.CLOSED);
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomAccess.requireParticipant(hostId, roomId)).thenReturn(host);

        roomMembershipService.leave(hostId, roomId);

        verifyNoInteractions(hostHandover, lessonLifecycle);
        verify(roomRepository, never()).save(any());
        verify(participantRepository).delete(host);
    }

    @Test
    void leave_UnknownRoom_IsNotFound() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomMembershipService.leave(studentId, roomId))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(participantNotifier, roomSubscriptionTracker);
    }

    @Test
    void leave_NotInTheRoom_IsNotFound() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomAccess.requireParticipant(studentId, roomId)).thenThrow(new RoomNotFoundException("Room not found"));

        assertThatThrownBy(() -> roomMembershipService.leave(studentId, roomId))
                .isInstanceOf(RoomNotFoundException.class);
        verify(participantRepository, never()).delete(any());
    }

    private void lockRoomAsHost() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(roomAccess.requireHost(eq(hostId), eq(roomId), anyString())).thenReturn(host);
    }
}
