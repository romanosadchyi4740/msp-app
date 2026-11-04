package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomAccessTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    UserLookup userLookup;

    @InjectMocks
    RoomAccess roomAccess;

    UUID userId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    Room room = Room.builder().id(roomId).build();

    @Test
    void requireUser_DeletedAccount_IsNotFound() {
        when(userLookup.exists(userId)).thenReturn(false);

        assertThatThrownBy(() -> roomAccess.requireUser(userId)).isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void requireUser_ExistingAccount_Passes() {
        when(userLookup.exists(userId)).thenReturn(true);

        assertThatCode(() -> roomAccess.requireUser(userId)).doesNotThrowAnyException();
    }

    @Test
    void requireParticipant_NotInTheRoom_IsNotFound() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomAccess.requireParticipant(userId, roomId))
                .isInstanceOf(RoomNotFoundException.class)
                .hasMessage("Room not found");
    }

    @Test
    void requireHost_Participant_IsForbiddenWithTheGivenMessage() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(participant(RoomRole.PARTICIPANT)));

        assertThatThrownBy(() -> roomAccess.requireHost(userId, roomId, "Only the host can do this"))
                .isInstanceOf(UserNotAuthorizedException.class)
                .hasMessage("Only the host can do this");
    }

    @Test
    void lockAsHost_Host_LocksBeforeCheckingTheRole() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(participant(RoomRole.HOST)));

        assertThat(roomAccess.lockAsHost(userId, roomId, "Only the host")).isSameAs(room);
        InOrder order = inOrder(roomRepository, participantRepository);
        order.verify(roomRepository).findByIdForUpdate(roomId);
        order.verify(participantRepository).findByRoomIdAndUserId(roomId, userId);
    }

    @Test
    void lockAsHost_NoRoom_IsNotFoundWithoutCheckingTheRole() {
        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomAccess.lockAsHost(userId, roomId, "Only the host"))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(participantRepository);
    }

    private RoomParticipant participant(RoomRole role) {
        return RoomParticipant.builder().room(room).userId(userId).role(role).build();
    }
}
