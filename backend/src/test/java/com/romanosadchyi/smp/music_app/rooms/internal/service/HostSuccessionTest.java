package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonEndReason;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomRole;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomStatus;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class HostSuccessionTest {

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    HostHandover hostHandover;

    @Mock
    LessonLifecycle lessonLifecycle;

    @InjectMocks
    HostSuccession hostSuccession;

    UUID roomId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    Room room = Room.builder().id(roomId).hostId(hostId).status(RoomStatus.OPEN).build();
    RoomParticipant host = RoomParticipant.builder().room(room).userId(hostId).role(RoomRole.HOST).build();
    RoomParticipant student = RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT)
            .online(true).build();

    @Test
    void afterHostLeft_PrefersTheEarliestOnlineParticipant() {
        when(participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(roomId, hostId))
                .thenReturn(Optional.of(student));

        hostSuccession.afterHostLeft(room, host);

        verify(hostHandover).transfer(room, host, student);
        verify(lessonLifecycle).pauseActive(roomId);
        verify(participantRepository, never()).findFirstByRoomIdAndUserIdNotOrderByJoinedAtAsc(any(), any());
    }

    @Test
    void afterHostLeft_NobodyOnline_HandsToTheEarliestJoined() {
        when(participantRepository.findFirstByRoomIdAndUserIdNotAndOnlineTrueOrderByJoinedAtAsc(roomId, hostId))
                .thenReturn(Optional.empty());
        when(participantRepository.findFirstByRoomIdAndUserIdNotOrderByJoinedAtAsc(roomId, hostId))
                .thenReturn(Optional.of(student));

        hostSuccession.afterHostLeft(room, host);

        verify(hostHandover).transfer(room, host, student);
        verify(lessonLifecycle).pauseActive(roomId);
        assertThat(room.getStatus()).isEqualTo(RoomStatus.OPEN);
    }

    @Test
    void afterHostLeft_LastOneInTheRoom_ClosesItAndEndsTheLesson() {
        hostSuccession.afterHostLeft(room, host);

        assertThat(room.getStatus()).isEqualTo(RoomStatus.CLOSED);
        verify(roomRepository).save(room);
        verify(lessonLifecycle).endActive(roomId, LessonEndReason.ROOM_CLOSED);
        verifyNoInteractions(hostHandover);
    }

    @Test
    void handOver_TransfersThenPausesThePlayback() {
        hostSuccession.handOver(room, host, student);

        InOrder order = inOrder(hostHandover, lessonLifecycle);
        order.verify(hostHandover).transfer(room, host, student);
        order.verify(lessonLifecycle).pauseActive(roomId);
    }
}
