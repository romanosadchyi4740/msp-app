package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomPreview;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomClosedException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomFullException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * With the real {@link RoomAccess} and {@link InviteCodes} built from the mocks, so the checks run as in production.
 */
@ExtendWith(MockitoExtension.class)
class RoomInviteServiceTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    RoomMappingService roomMappingService;

    @Mock
    UserLookup userLookup;

    @Mock
    InviteCodeGenerator inviteCodeGenerator;

    @Mock
    ParticipantNotifier participantNotifier;

    RoomInviteService roomInviteService;

    UUID hostId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    RoomResponse response = new RoomResponse();

    @BeforeEach
    void setUp() {
        lenient().when(userLookup.exists(any())).thenReturn(true);
        RoomAccess roomAccess = new RoomAccess(roomRepository, participantRepository, userLookup);
        roomInviteService = new RoomInviteService(roomRepository, participantRepository,
                new InviteCodes(inviteCodeGenerator, roomRepository), roomAccess, roomMappingService,
                participantNotifier);
    }

    @Test
    void join_NewUserInOpenRoomWithAFreePlace_AddsAParticipantUnderTheLock() {
        Room room = room(RoomStatus.OPEN);
        when(roomRepository.findByInviteCodeForUpdate("code")).thenReturn(Optional.of(room));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());
        when(participantRepository.countByRoomId(roomId)).thenReturn(4L);
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        RoomResponse result = roomInviteService.join(userId, "code");

        ArgumentCaptor<RoomParticipant> participant = ArgumentCaptor.forClass(RoomParticipant.class);
        InOrder order = inOrder(roomRepository, participantRepository);
        order.verify(roomRepository).findByInviteCodeForUpdate("code");
        order.verify(participantRepository).save(participant.capture());
        assertThat(participant.getValue().getUserId()).isEqualTo(userId);
        assertThat(participant.getValue().getRole()).isEqualTo(RoomRole.PARTICIPANT);
        assertThat(participant.getValue().getRoom()).isSameAs(room);
        verify(roomRepository, never()).findByInviteCode(any());
        verify(participantNotifier).publish(roomId, new ParticipantEvent(ParticipantEventType.JOINED, userId,
                room.getHostId()));
        assertThat(result).isSameAs(response);
    }

    @Test
    void join_FullRoom_IsRejectedWithRoomFull() {
        when(roomRepository.findByInviteCodeForUpdate("code")).thenReturn(Optional.of(room(RoomStatus.OPEN)));
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());
        when(participantRepository.countByRoomId(roomId)).thenReturn(5L);

        assertThatThrownBy(() -> roomInviteService.join(userId, "code"))
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

        assertThat(roomInviteService.join(userId, "code")).isSameAs(response);
        verify(participantRepository, never()).countByRoomId(any());
        verify(participantRepository, never()).save(any());
        verifyNoInteractions(participantNotifier);
    }

    @Test
    void join_ClosedRoom_IsRejected() {
        when(roomRepository.findByInviteCodeForUpdate("code")).thenReturn(Optional.of(room(RoomStatus.CLOSED)));

        assertThatThrownBy(() -> roomInviteService.join(userId, "code")).isInstanceOf(RoomClosedException.class);
        verify(participantRepository, never()).save(any());
    }

    @Test
    void join_DeletedAccount_IsRejectedBeforeLockingTheRoom() {
        when(userLookup.exists(userId)).thenReturn(false);

        assertThatThrownBy(() -> roomInviteService.join(userId, "code")).isInstanceOf(UserNotFoundException.class);
        verifyNoInteractions(roomRepository, participantRepository);
    }

    @Test
    void join_UnknownCode_IsNotFound() {
        when(roomRepository.findByInviteCodeForUpdate("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomInviteService.join(userId, "nope")).isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void regenerateInvite_Host_ReplacesTheCode() {
        Room room = room(RoomStatus.OPEN);
        when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(room, hostId, RoomRole.HOST)));
        when(inviteCodeGenerator.generate()).thenReturn("NewCode00000");
        when(roomMappingService.map(any(), any(), any())).thenReturn(response);

        assertThat(roomInviteService.regenerateInvite(hostId, roomId)).isSameAs(response);
        assertThat(room.getInviteCode()).isEqualTo("NewCode00000");
        verify(roomRepository).save(room);
    }

    @Test
    void regenerateInvite_Participant_IsForbidden() {
        Room room = room(RoomStatus.OPEN);
        when(participantRepository.findByRoomIdAndUserId(roomId, userId))
                .thenReturn(Optional.of(participant(room, userId, RoomRole.PARTICIPANT)));

        assertThatThrownBy(() -> roomInviteService.regenerateInvite(userId, roomId))
                .isInstanceOf(UserNotAuthorizedException.class);
        assertThat(room.getInviteCode()).isEqualTo("OldCode00000");
        verifyNoInteractions(inviteCodeGenerator);
    }

    @Test
    void regenerateInvite_ClosedRoom_IsRejected() {
        Room room = room(RoomStatus.CLOSED);
        when(participantRepository.findByRoomIdAndUserId(roomId, hostId))
                .thenReturn(Optional.of(participant(room, hostId, RoomRole.HOST)));

        assertThatThrownBy(() -> roomInviteService.regenerateInvite(hostId, roomId))
                .isInstanceOf(RoomClosedException.class);
        verifyNoInteractions(inviteCodeGenerator);
    }

    @Test
    void regenerateInvite_NotAParticipant_IsNotFound() {
        when(participantRepository.findByRoomIdAndUserId(roomId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomInviteService.regenerateInvite(userId, roomId))
                .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void getPreview_KnownCode_MapsTheRoomWithItsParticipantCount() {
        Room room = room(RoomStatus.OPEN);
        RoomPreview preview = new RoomPreview();
        when(roomRepository.findByInviteCode("code")).thenReturn(Optional.of(room));
        when(participantRepository.countByRoomId(roomId)).thenReturn(3L);
        when(roomMappingService.mapPreview(room, 3L)).thenReturn(preview);

        assertThat(roomInviteService.getPreview("code")).isSameAs(preview);
    }

    @Test
    void getPreview_UnknownCode_IsNotFound() {
        when(roomRepository.findByInviteCode("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomInviteService.getPreview("nope")).isInstanceOf(RoomNotFoundException.class);
    }

    private Room room(RoomStatus status) {
        return Room.builder().id(roomId).hostId(hostId).name("Lesson").inviteCode("OldCode00000").status(status)
                .build();
    }

    private static RoomParticipant participant(Room room, UUID userId, RoomRole role) {
        return RoomParticipant.builder().id(UUID.randomUUID()).room(room).userId(userId).role(role).build();
    }
}
