package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;

@ExtendWith(MockitoExtension.class)
class HostHandoverTest {

    static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    ParticipantNotifier participantNotifier;

    HostHandover hostHandover;

    UUID roomId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    Room room = Room.builder().id(roomId).hostId(hostId).build();
    RoomParticipant host = RoomParticipant.builder().room(room).userId(hostId).role(RoomRole.HOST).build();
    RoomParticipant student = RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT)
            .online(true).build();

    @BeforeEach
    void setUp() {
        hostHandover = new HostHandover(roomRepository, participantRepository, participantNotifier,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void transfer_DemotesTheHostFirstThenPromotesAndTellsTheRoom() {
        hostHandover.transfer(room, host, student);

        InOrder order = inOrder(participantRepository, roomRepository, participantNotifier);
        order.verify(participantRepository).saveAndFlush(host);
        order.verify(participantRepository).save(student);
        order.verify(roomRepository).save(room);
        order.verify(participantNotifier).publish(roomId,
                new ParticipantEvent(ParticipantEventType.HOST_CHANGED, studentId, studentId));
        assertThat(host.getRole()).isEqualTo(RoomRole.PARTICIPANT);
        assertThat(student.getRole()).isEqualTo(RoomRole.HOST);
        assertThat(room.getHostId()).isEqualTo(studentId);
    }

    @Test
    void transfer_ToSomeoneNeverSeenOnline_StartsTheirAwayTimeNow() {
        student.setOnline(false);

        hostHandover.transfer(room, host, student);

        assertThat(student.getOfflineSince()).isEqualTo(NOW);
    }

    @Test
    void transfer_ToSomeoneOfflineSinceEarlier_KeepsTheirOfflineTime() {
        Instant earlier = NOW.minusSeconds(300);
        student.setOnline(false);
        student.setOfflineSince(earlier);

        hostHandover.transfer(room, host, student);

        assertThat(student.getOfflineSince()).isEqualTo(earlier);
    }

    @Test
    void transfer_ToSomeoneOnline_LeavesTheOfflineTimeEmpty() {
        hostHandover.transfer(room, host, student);

        assertThat(student.getOfflineSince()).isNull();
    }
}
