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
class RoomPresenceServiceTest {

    static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock
    RoomRepository roomRepository;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    ParticipantNotifier participantNotifier;

    RoomPresenceService roomPresenceService;

    UUID roomId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    Room room = Room.builder().id(roomId).hostId(hostId).build();
    RoomParticipant student = RoomParticipant.builder().room(room).userId(studentId).role(RoomRole.PARTICIPANT)
            .build();

    @BeforeEach
    void setUp() {
        roomPresenceService = new RoomPresenceService(roomRepository, participantRepository, participantNotifier,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void markOnline_OfflineParticipant_StoresAndTellsTheRoom() {
        student.setOfflineSince(NOW.minusSeconds(30));
        when(participantRepository.findByRoomIdAndUserId(roomId, studentId)).thenReturn(Optional.of(student));

        roomPresenceService.markOnline(roomId, studentId);

        InOrder order = inOrder(roomRepository, participantRepository);
        order.verify(roomRepository).findByIdForUpdate(roomId);
        order.verify(participantRepository).findByRoomIdAndUserId(roomId, studentId);
        assertThat(student.isOnline()).isTrue();
        assertThat(student.getOfflineSince()).isNull();
        verify(participantRepository).save(student);
        verify(participantNotifier).publish(roomId,
                new ParticipantEvent(ParticipantEventType.ONLINE, studentId, hostId));
    }

    @Test
    void markOnline_AlreadyOnline_ChangesNothing() {
        student.setOnline(true);
        when(participantRepository.findByRoomIdAndUserId(roomId, studentId)).thenReturn(Optional.of(student));

        roomPresenceService.markOnline(roomId, studentId);

        verify(participantRepository, never()).save(any());
        verifyNoInteractions(participantNotifier);
    }

    @Test
    void markOnline_NotAParticipant_IsIgnored() {
        when(participantRepository.findByRoomIdAndUserId(roomId, studentId)).thenReturn(Optional.empty());

        roomPresenceService.markOnline(roomId, studentId);

        verifyNoInteractions(participantNotifier);
    }

    @Test
    void markOffline_OnlineParticipant_StoresTheTimeAndTellsTheRoom() {
        student.setOnline(true);
        when(participantRepository.findByRoomIdAndUserId(roomId, studentId)).thenReturn(Optional.of(student));

        roomPresenceService.markOffline(roomId, studentId);

        InOrder order = inOrder(roomRepository, participantRepository);
        order.verify(roomRepository).findByIdForUpdate(roomId);
        order.verify(participantRepository).findByRoomIdAndUserId(roomId, studentId);
        assertThat(student.isOnline()).isFalse();
        assertThat(student.getOfflineSince()).isEqualTo(NOW);
        verify(participantRepository).save(student);
        verify(participantNotifier).publish(roomId,
                new ParticipantEvent(ParticipantEventType.OFFLINE, studentId, hostId));
    }

    @Test
    void markOffline_AlreadyOfflineOrGone_ChangesNothing() {
        when(participantRepository.findByRoomIdAndUserId(roomId, studentId))
                .thenReturn(Optional.of(student), Optional.empty());

        roomPresenceService.markOffline(roomId, studentId);
        roomPresenceService.markOffline(roomId, studentId);

        verify(participantRepository, never()).save(any());
        verifyNoInteractions(participantNotifier);
    }

    @Test
    void markAllOffline_MarksEveryoneOfflineFromNow() {
        roomPresenceService.markAllOffline();

        verify(participantRepository).markAllOffline(NOW);
    }
}
