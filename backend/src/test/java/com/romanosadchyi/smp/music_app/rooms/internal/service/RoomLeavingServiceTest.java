package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomLeavingServiceTest {

    @Mock
    RoomAccess roomAccess;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    HostSuccession hostSuccession;

    @Mock
    ParticipantNotifier participantNotifier;

    @Mock
    RoomSubscriptionTracker roomSubscriptionTracker;

    @InjectMocks
    RoomLeavingService roomLeavingService;

    UUID roomId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    Room room = Room.builder().id(roomId).hostId(hostId).status(RoomStatus.OPEN).build();
    RoomParticipant host = RoomParticipant.builder().room(room).userId(hostId).role(RoomRole.HOST).build();
    RoomParticipant student = RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT)
            .build();

    @Test
    void leave_Participant_FreesThePlaceTellsTheRoomAndEndsTheSubscriptions() {
        when(roomAccess.lock(roomId)).thenReturn(room);
        when(roomAccess.requireParticipant(studentId, roomId)).thenReturn(student);

        roomLeavingService.leave(studentId, roomId);

        InOrder order = inOrder(roomAccess, participantRepository, participantNotifier, roomSubscriptionTracker);
        order.verify(roomAccess).lock(roomId);
        order.verify(roomAccess).requireParticipant(studentId, roomId);
        order.verify(participantRepository).delete(student);
        order.verify(participantNotifier).publish(roomId,
                new ParticipantEvent(ParticipantEventType.LEFT, studentId, hostId));
        order.verify(roomSubscriptionTracker).revoke(roomId, studentId);
        verifyNoInteractions(hostSuccession);
    }

    @Test
    void leave_HostOfAnOpenRoom_PassesTheRoomOnBeforeLeaving() {
        when(roomAccess.lock(roomId)).thenReturn(room);
        when(roomAccess.requireParticipant(hostId, roomId)).thenReturn(host);

        roomLeavingService.leave(hostId, roomId);

        InOrder order = inOrder(hostSuccession, participantRepository);
        order.verify(hostSuccession).afterHostLeft(room, host);
        order.verify(participantRepository).delete(host);
    }

    @Test
    void leave_HostOfAClosedRoom_HandsNothingOver() {
        room.setStatus(RoomStatus.CLOSED);
        when(roomAccess.lock(roomId)).thenReturn(room);
        when(roomAccess.requireParticipant(hostId, roomId)).thenReturn(host);

        roomLeavingService.leave(hostId, roomId);

        verifyNoInteractions(hostSuccession);
        verify(participantRepository).delete(host);
    }

    @Test
    void leave_UnknownRoom_IsNotFound() {
        when(roomAccess.lock(roomId)).thenThrow(new RoomNotFoundException("Room not found"));

        assertThatThrownBy(() -> roomLeavingService.leave(studentId, roomId))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(participantNotifier, roomSubscriptionTracker);
    }

    @Test
    void leave_NotInTheRoom_IsNotFound() {
        when(roomAccess.lock(roomId)).thenReturn(room);
        when(roomAccess.requireParticipant(studentId, roomId)).thenThrow(new RoomNotFoundException("Room not found"));

        assertThatThrownBy(() -> roomLeavingService.leave(studentId, roomId))
                .isInstanceOf(RoomNotFoundException.class);
        verify(participantRepository, never()).delete(any());
    }
}
