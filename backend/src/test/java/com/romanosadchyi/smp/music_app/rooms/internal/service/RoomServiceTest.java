package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomCreateRequest;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomListItem;
import com.romanosadchyi.smp.music_app.rooms.internal.api.model.RoomResponse;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.mapping.RoomMappingService;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantCount;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import com.romanosadchyi.smp.music_app.users.external.UserLookup;
import com.romanosadchyi.smp.music_app.users.external.UserNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * With the real {@link RoomAccess} and {@link InviteCodes} built from the mocks, so the checks run as in production.
 */
@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

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

    RoomService roomService;

    UUID hostId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();
    RoomResponse response = new RoomResponse();

    @BeforeEach
    void setUp() {
        lenient().when(userLookup.exists(any())).thenReturn(true);
        RoomAccess roomAccess = new RoomAccess(roomRepository, participantRepository, userLookup);
        roomService = new RoomService(roomRepository, participantRepository,
                new InviteCodes(inviteCodeGenerator, roomRepository), roomAccess, roomMappingService);
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
        verify(inviteCodeGenerator, times(InviteCodes.MAX_ATTEMPTS)).generate();
        verify(roomRepository, never()).save(any());
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
