package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostAwayServiceTest {

    static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    HostSuccession hostSuccession;

    HostAwayService hostAwayService;

    UUID roomId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    Room room = Room.builder().id(roomId).hostId(hostId).status(RoomStatus.OPEN).build();
    RoomParticipant host = RoomParticipant.builder().room(room).userId(hostId).role(RoomRole.HOST).build();
    RoomParticipant student = RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT)
            .online(true).build();

    @BeforeEach
    void setUp() {
        hostAwayService = new HostAwayService(roomRepository, participantRepository, hostSuccession,
                Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.of(room));
        lenient().when(participantRepository.findByRoomIdAndRole(roomId, RoomRole.HOST)).thenReturn(Optional.of(host));
        lenient().when(participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(roomId,
                hostId)).thenReturn(Optional.of(student));
    }

    @Test
    void findRoomsWithHostAway_LooksSixtySecondsBack() {
        when(participantRepository.findRoomsWithHostOfflineSince(NOW.minusSeconds(60), RoomRole.HOST,
                RoomStatus.OPEN)).thenReturn(List.of(roomId));

        assertThat(hostAwayService.findRoomsWithHostAway()).containsExactly(roomId);
    }

    @Test
    void handOver_HostAwayLongerThanTheLimit_PassesToTheEarliestOnlineAndPauses() {
        host.setOfflineSince(NOW.minusSeconds(61));

        hostAwayService.handOver(roomId);

        verify(hostSuccession).handOver(room, host, student);
    }

    @Test
    void handOver_HostAwayExactlyTheLimit_PassesOn() {
        host.setOfflineSince(NOW.minusSeconds(60));

        hostAwayService.handOver(roomId);

        verify(hostSuccession).handOver(room, host, student);
    }

    @Test
    void handOver_HostAwayShorterThanTheLimit_ChangesNothing() {
        host.setOfflineSince(NOW.minusSeconds(30));

        hostAwayService.handOver(roomId);

        verifyNoInteractions(hostSuccession);
    }

    @Test
    void handOver_HostBackMeanwhile_ChangesNothing() {
        host.setOnline(true);

        hostAwayService.handOver(roomId);

        verifyNoInteractions(hostSuccession);
    }

    @Test
    void handOver_HostNeverSeenOnline_KeepsTheRole() {
        hostAwayService.handOver(roomId);

        verifyNoInteractions(hostSuccession);
    }

    @Test
    void handOver_NobodyElseOnline_ChangesNothing() {
        host.setOfflineSince(NOW.minusSeconds(120));
        when(participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(roomId, hostId))
                .thenReturn(Optional.empty());

        hostAwayService.handOver(roomId);

        verifyNoInteractions(hostSuccession);
    }

    @Test
    void handOver_ClosedOrDeletedRoom_ChangesNothing() {
        room.setStatus(RoomStatus.CLOSED);
        hostAwayService.handOver(roomId);

        when(roomRepository.findByIdForUpdate(roomId)).thenReturn(Optional.empty());
        hostAwayService.handOver(roomId);

        verifyNoInteractions(hostSuccession);
    }
}
