package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.RoomLessonAccess;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.RoomSyncSnapshot;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommandType;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomSyncServiceTest {

    static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock
    RoomLessonAccess roomLessonAccess;

    @Mock
    PlaybackStateService playbackStateService;

    @Mock
    PlaybackNotifier playbackNotifier;

    RoomSyncService roomSyncService;

    UUID roomId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();
    UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        roomSyncService = new RoomSyncService(roomLessonAccess, playbackStateService, playbackNotifier,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void command_FromHost_LocksTheRoomFirstAppliesAndPublishes() {
        PlaybackStateDto state = state(4L);
        when(roomLessonAccess.lockActiveLessonAsHost(roomId, hostId)).thenReturn(sessionId);
        when(playbackStateService.apply(any(), any())).thenReturn(state);

        roomSyncService.handleCommand(roomId, hostId, new SyncCommand(SyncCommandType.PLAY, null, null, 77L));

        InOrder order = inOrder(roomLessonAccess, playbackStateService, playbackNotifier);
        order.verify(roomLessonAccess).lockActiveLessonAsHost(roomId, hostId);
        ArgumentCaptor<SyncCommand> applied = ArgumentCaptor.forClass(SyncCommand.class);
        order.verify(playbackStateService).apply(eq(sessionId), applied.capture());
        order.verify(playbackNotifier).publishState(roomId, state);
        assertThat(applied.getValue()).isEqualTo(new SyncCommand(SyncCommandType.PLAY, null, hostId, 77L));
    }

    @Test
    void command_ClaimedSender_IsReplacedByTheSessionUser() {
        when(roomLessonAccess.lockActiveLessonAsHost(roomId, hostId)).thenReturn(sessionId);
        when(playbackStateService.apply(any(), any())).thenReturn(state(1L));

        roomSyncService.handleCommand(roomId, hostId, new SyncCommand(SyncCommandType.SEEK, "3", studentId, null));

        ArgumentCaptor<SyncCommand> applied = ArgumentCaptor.forClass(SyncCommand.class);
        verify(playbackStateService).apply(eq(sessionId), applied.capture());
        assertThat(applied.getValue().senderId()).isEqualTo(hostId);
    }

    @Test
    void command_FromParticipant_ChangesNothing() {
        when(roomLessonAccess.lockActiveLessonAsHost(roomId, studentId))
                .thenThrow(new UserNotAuthorizedException("Only the host can control playback"));

        assertThatThrownBy(() -> roomSyncService.handleCommand(roomId, studentId,
                new SyncCommand(SyncCommandType.PLAY, null, null, null)))
                .isInstanceOf(UserNotAuthorizedException.class);
        verifyNoInteractions(playbackStateService, playbackNotifier);
    }

    @Test
    void command_WithoutType_IsRejected() {
        assertThatThrownBy(() -> roomSyncService.handleCommand(roomId, hostId, new SyncCommand(null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> roomSyncService.handleCommand(roomId, hostId, null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(roomLessonAccess, playbackStateService, playbackNotifier);
    }

    @Test
    void requestSync_FromAnyParticipant_SendsASnapshotToThemOnly() {
        PlaybackStateDto state = state(9L);
        when(roomLessonAccess.findActiveLesson(roomId, studentId)).thenReturn(Optional.of(sessionId));
        when(playbackStateService.snapshot(sessionId)).thenReturn(state);

        roomSyncService.handleCommand(roomId, studentId,
                new SyncCommand(SyncCommandType.REQUEST_SYNC, null, null, null));

        verify(playbackNotifier).sendSnapshot(studentId,
                new RoomSyncSnapshot(roomId, sessionId, state, NOW.toEpochMilli()));
        verify(roomLessonAccess, never()).lockActiveLessonAsHost(any(), any());
        verify(playbackNotifier, never()).publishState(any(), any());
    }

    @Test
    void snapshot_WithoutLesson_HasNoPlayback() {
        when(roomLessonAccess.findActiveLesson(roomId, studentId)).thenReturn(Optional.empty());

        RoomSyncSnapshot snapshot = roomSyncService.snapshot(roomId, studentId);

        assertThat(snapshot).isEqualTo(new RoomSyncSnapshot(roomId, null, null, NOW.toEpochMilli()));
        verifyNoInteractions(playbackStateService);
    }

    private PlaybackStateDto state(long version) {
        return new PlaybackStateDto(sessionId, 1, 0, 120, 4, 4, PlaybackStatus.PLAYING, NOW.toEpochMilli() + 2000,
                false, version, NOW.toEpochMilli());
    }
}
