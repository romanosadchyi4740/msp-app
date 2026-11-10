package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.api.model.QualityReport;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEvent;
import com.romanosadchyi.smp.music_app.rooms.internal.dto.ParticipantEventType;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.RoomParticipantRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomQualityServiceTest {

    @Mock
    RoomAccess roomAccess;

    @Mock
    RoomParticipantRepository participantRepository;

    @Mock
    ParticipantNotifier participantNotifier;

    @InjectMocks
    RoomQualityService roomQualityService;

    UUID roomId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();
    UUID participantId = UUID.randomUUID();

    @Test
    void report_NewTime_IsStoredAndToldToTheRoom() {
        when(roomAccess.requireParticipant(studentId, roomId)).thenReturn(participant(null));

        roomQualityService.report(studentId, roomId, new QualityReport().rttMs(84));

        verify(participantRepository).updateRttMs(participantId, 84);
        verify(participantNotifier).publish(roomId,
                new ParticipantEvent(ParticipantEventType.RTT, studentId, hostId, 84));
    }

    @Test
    void report_ChangedTime_ReplacesTheOldOne() {
        when(roomAccess.requireParticipant(studentId, roomId)).thenReturn(participant(84));

        roomQualityService.report(studentId, roomId, new QualityReport().rttMs(0));

        verify(participantRepository).updateRttMs(participantId, 0);
        verify(participantNotifier).publish(roomId,
                new ParticipantEvent(ParticipantEventType.RTT, studentId, hostId, 0));
    }

    @Test
    void report_SameTimeAsStored_ChangesAndTellsNothing() {
        when(roomAccess.requireParticipant(studentId, roomId)).thenReturn(participant(84));

        roomQualityService.report(studentId, roomId, new QualityReport().rttMs(84));

        verify(participantRepository, never()).updateRttMs(any(), anyInt());
        verifyNoInteractions(participantNotifier);
    }

    @Test
    void report_UpperLimit_IsAccepted() {
        when(roomAccess.requireParticipant(studentId, roomId)).thenReturn(participant(null));

        roomQualityService.report(studentId, roomId, new QualityReport().rttMs(RoomQualityService.MAX_RTT_MS));

        verify(participantRepository).updateRttMs(participantId, RoomQualityService.MAX_RTT_MS);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, RoomQualityService.MAX_RTT_MS + 1, Integer.MAX_VALUE})
    void report_TimeOutOfRange_IsABadRequest(int rttMs) {
        assertThatThrownBy(() -> roomQualityService.report(studentId, roomId, new QualityReport().rttMs(rttMs)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomAccess, participantRepository, participantNotifier);
    }

    @Test
    void report_NoTime_IsABadRequest() {
        assertThatThrownBy(() -> roomQualityService.report(studentId, roomId, new QualityReport()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roomQualityService.report(studentId, roomId, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomAccess, participantRepository, participantNotifier);
    }

    @Test
    void report_NotInTheRoom_IsNotFound() {
        when(roomAccess.requireParticipant(studentId, roomId)).thenThrow(new RoomNotFoundException("Room not found"));

        assertThatThrownBy(() -> roomQualityService.report(studentId, roomId, new QualityReport().rttMs(84)))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(participantRepository, participantNotifier);
    }

    private RoomParticipant participant(Integer rttMs) {
        return RoomParticipant.builder()
                .id(participantId)
                .room(Room.builder().id(roomId).hostId(hostId).build())
                .userId(studentId)
                .rttMs(rttMs)
                .build();
    }
}
