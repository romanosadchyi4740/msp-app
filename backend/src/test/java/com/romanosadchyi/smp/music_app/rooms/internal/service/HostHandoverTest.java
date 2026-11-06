package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.LessonPlayback;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostHandoverTest {

    static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    LessonSessionRepository lessonRepository;

    @Mock
    LessonPlayback lessonPlayback;

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
        hostHandover = new HostHandover(roomRepository, participantRepository, lessonRepository, lessonPlayback,
                participantNotifier, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void successor_PrefersTheEarliestOnlineParticipant() {
        when(participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(roomId, hostId))
                .thenReturn(Optional.of(student));

        assertThat(hostHandover.successor(roomId, hostId)).contains(student);
        verify(participantRepository, never()).findFirstByRoomIdAndUserIdNotOrderByJoinedAtAsc(any(), any());
    }

    @Test
    void successor_NobodyOnline_IsTheEarliestJoined() {
        when(participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(roomId, hostId))
                .thenReturn(Optional.empty());
        when(participantRepository.findFirstByRoomIdAndUserIdNotOrderByJoinedAtAsc(roomId, hostId))
                .thenReturn(Optional.of(student));

        assertThat(hostHandover.successor(roomId, hostId)).contains(student);
    }

    @Test
    void successor_AloneInTheRoom_IsEmpty() {
        assertThat(hostHandover.successor(roomId, hostId)).isEmpty();
    }

    @Test
    void transfer_DemotesTheHostFirstThenPromotesAndTellsTheRoom() {
        hostHandover.transfer(room, host, student, false);

        InOrder order = inOrder(participantRepository, roomRepository, participantNotifier);
        order.verify(participantRepository).saveAndFlush(host);
        order.verify(participantRepository).save(student);
        order.verify(roomRepository).save(room);
        order.verify(participantNotifier).publish(roomId,
                new ParticipantEvent(ParticipantEventType.HOST_CHANGED, studentId, studentId));
        assertThat(host.getRole()).isEqualTo(RoomRole.PARTICIPANT);
        assertThat(student.getRole()).isEqualTo(RoomRole.HOST);
        assertThat(room.getHostId()).isEqualTo(studentId);
        verifyNoInteractions(lessonRepository, lessonPlayback);
    }

    @Test
    void transfer_ToSomeoneNeverSeenOnline_StartsTheirAwayTimeNow() {
        student.setOnline(false);

        hostHandover.transfer(room, host, student, false);

        assertThat(student.getOfflineSince()).isEqualTo(NOW);
    }

    @Test
    void transfer_ToSomeoneOfflineSinceEarlier_KeepsTheirOfflineTime() {
        Instant earlier = NOW.minusSeconds(300);
        student.setOnline(false);
        student.setOfflineSince(earlier);

        hostHandover.transfer(room, host, student, false);

        assertThat(student.getOfflineSince()).isEqualTo(earlier);
    }

    @Test
    void transfer_ToSomeoneOnline_LeavesTheOfflineTimeEmpty() {
        hostHandover.transfer(room, host, student, false);

        assertThat(student.getOfflineSince()).isNull();
    }

    @Test
    void transfer_WithPause_PausesTheActiveLesson() {
        UUID lessonId = UUID.randomUUID();
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId))
                .thenReturn(Optional.of(LessonSession.builder().id(lessonId).build()));

        hostHandover.transfer(room, host, student, true);

        verify(lessonPlayback).pause(roomId, lessonId);
    }

    @Test
    void transfer_WithPauseButNoLesson_PausesNothing() {
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.empty());

        hostHandover.transfer(room, host, student, true);

        verifyNoInteractions(lessonPlayback);
    }
}
