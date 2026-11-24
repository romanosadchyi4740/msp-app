package com.romanosadchyi.smp.music_app.rooms.internal.service;

import com.romanosadchyi.smp.music_app.rooms.internal.entity.LessonSession;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.Room;
import com.romanosadchyi.smp.music_app.rooms.internal.entity.RoomParticipant;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.LessonNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.exception.RoomNotFoundException;
import com.romanosadchyi.smp.music_app.rooms.internal.repository.LessonSessionRepository;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
class RoomLessonAccessServiceTest {

    @Mock
    RoomAccess roomAccess;

    @Mock
    LessonSessionRepository lessonRepository;

    @InjectMocks
    RoomLessonAccessService roomLessonAccess;

    UUID roomId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();

    @Test
    void lockActiveLessonAsHost_LocksTheRoomThenReturnsTheLesson() {
        when(roomAccess.lockAsHost(userId, roomId, "Only the host can control playback")).thenReturn(new Room());
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson()));

        assertThat(roomLessonAccess.lockActiveLessonAsHost(roomId, userId)).isEqualTo(sessionId);

        InOrder order = inOrder(roomAccess, lessonRepository);
        order.verify(roomAccess).lockAsHost(userId, roomId, "Only the host can control playback");
        order.verify(lessonRepository).findByRoomIdAndEndedAtIsNull(roomId);
    }

    @Test
    void lockActiveLessonAsHost_NotHost_IsForbidden() {
        when(roomAccess.lockAsHost(eq(userId), eq(roomId),
                anyString())).thenThrow(new UserNotAuthorizedException("Only the host can control playback"));

        assertThatThrownBy(() -> roomLessonAccess.lockActiveLessonAsHost(roomId, userId))
                .isInstanceOf(UserNotAuthorizedException.class);
        verifyNoInteractions(lessonRepository);
    }

    @Test
    void lockActiveLessonAsHost_WithoutLesson_IsNotFound() {
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomLessonAccess.lockActiveLessonAsHost(roomId, userId))
                .isInstanceOf(LessonNotFoundException.class);
    }

    @Test
    void findActiveLesson_Participant() {
        when(roomAccess.requireParticipant(userId, roomId)).thenReturn(new RoomParticipant());
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.of(lesson()));

        assertThat(roomLessonAccess.findActiveLesson(roomId, userId)).contains(sessionId);
    }

    @Test
    void findActiveLesson_NoLesson_IsEmpty() {
        when(lessonRepository.findByRoomIdAndEndedAtIsNull(roomId)).thenReturn(Optional.empty());

        assertThat(roomLessonAccess.findActiveLesson(roomId, userId)).isEmpty();
    }

    @Test
    void findActiveLesson_NotInTheRoom_IsNotFound() {
        when(roomAccess.requireParticipant(userId, roomId)).thenThrow(new RoomNotFoundException("Room not found"));

        assertThatThrownBy(() -> roomLessonAccess.findActiveLesson(roomId, userId))
                .isInstanceOf(RoomNotFoundException.class);
        verifyNoInteractions(lessonRepository);
    }

    private LessonSession lesson() {
        return LessonSession.builder().id(sessionId).build();
    }
}
