package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.HostTransferRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomParticipantNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostTransferServiceTest {

    @Mock
    RoomAccess roomAccess;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    HostHandover hostHandover;

    @Mock
    RoomMappingService roomMappingService;

    @InjectMocks
    HostTransferService hostTransferService;

    UUID roomId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    Room room = Room.builder().id(roomId).hostId(hostId).status(RoomStatus.OPEN).build();
    RoomParticipant host = RoomParticipant.builder().room(room).userId(hostId).role(RoomRole.HOST).build();
    RoomParticipant student = RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT)
            .build();

    @Test
    void transferHost_ToAParticipant_LocksThenHandsOver() {
        RoomResponse response = new RoomResponse();
        lockRoomAsHost();
        when(participantRepository.findByRoomIdAndUserId(roomId, studentId)).thenReturn(Optional.of(student));
        when(participantRepository.findAllByRoomIdOrderByJoinedAtAsc(roomId)).thenReturn(List.of(host, student));
        when(roomMappingService.map(room, List.of(host, student), hostId)).thenReturn(response);

        assertThat(hostTransferService.transferHost(hostId, roomId, new HostTransferRequest().userId(studentId)))
                .isSameAs(response);

        InOrder order = inOrder(roomAccess, hostHandover);
        order.verify(roomAccess).lock(roomId);
        order.verify(roomAccess).requireHost(eq(hostId), eq(roomId), anyString());
        order.verify(hostHandover).transfer(room, host, student);
    }

    @Test
    void transferHost_WithoutNewHost_IsABadRequest() {
        assertThatThrownBy(() -> hostTransferService.transferHost(hostId, roomId, new HostTransferRequest()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> hostTransferService.transferHost(hostId, roomId, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomAccess, hostHandover);
    }

    @Test
    void transferHost_ByParticipant_IsForbidden() {
        when(roomAccess.lock(roomId)).thenReturn(room);
        when(roomAccess.requireHost(eq(studentId), eq(roomId), anyString()))
                .thenThrow(new UserNotAuthorizedException("Only the host can hand over the room"));

        assertThatThrownBy(() -> hostTransferService.transferHost(studentId, roomId,
                new HostTransferRequest().userId(hostId)))
                .isInstanceOf(UserNotAuthorizedException.class);
        verifyNoInteractions(hostHandover);
    }

    @Test
    void transferHost_ClosedRoom_IsAConflict() {
        room.setStatus(RoomStatus.CLOSED);
        lockRoomAsHost();

        assertThatThrownBy(() -> hostTransferService.transferHost(hostId, roomId,
                new HostTransferRequest().userId(studentId)))
                .isInstanceOf(RoomClosedException.class);
        verifyNoInteractions(hostHandover);
    }

    @Test
    void transferHost_ToThemselves_IsABadRequest() {
        lockRoomAsHost();

        assertThatThrownBy(() -> hostTransferService.transferHost(hostId, roomId,
                new HostTransferRequest().userId(hostId)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(hostHandover);
    }

    @Test
    void transferHost_ToSomeoneNotInTheRoom_IsNotFound() {
        UUID outsiderId = UUID.randomUUID();
        lockRoomAsHost();
        when(participantRepository.findByRoomIdAndUserId(roomId, outsiderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hostTransferService.transferHost(hostId, roomId,
                new HostTransferRequest().userId(outsiderId)))
                .isInstanceOf(RoomParticipantNotFoundException.class);
        verifyNoInteractions(hostHandover);
    }

    private void lockRoomAsHost() {
        when(roomAccess.lock(roomId)).thenReturn(room);
        when(roomAccess.requireHost(eq(hostId), eq(roomId), anyString())).thenReturn(host);
    }
}
