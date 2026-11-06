package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommandType;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import com.romanosadchyi.smp.music_app.sync.internal.exception.PlaybackStateNotFoundException;
import com.romanosadchyi.smp.music_app.sync.internal.mapping.PlaybackStateMappingService;
import com.romanosadchyi.smp.music_app.sync.internal.repository.PlaybackStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaybackStateServiceTest {

    static final Instant NOW = Instant.parse("2026-11-06T10:00:00Z");

    @Mock
    PlaybackStateRepository playbackStateRepository;

    PlaybackStateService playbackStateService;

    UUID sessionId = UUID.randomUUID();
    UUID hostId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        playbackStateService = new PlaybackStateService(playbackStateRepository, new PlaybackStateMappingService(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void apply_LocksAppliesAtTheServerTimeAndReturnsTheSavedState() {
        PlaybackState state = stopped();
        PlaybackState saved = stopped();
        saved.setStatus(PlaybackStatus.PLAYING);
        saved.setAnchorTime(NOW.toEpochMilli() + 1500);
        saved.setVersion(3L);
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(state));
        when(playbackStateRepository.saveAndFlush(state)).thenReturn(saved);

        PlaybackStateDto result = playbackStateService.apply(sessionId,
                new SyncCommand(SyncCommandType.PLAY, null, hostId, 123L));

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.PLAYING);
        assertThat(state.getAnchorTime()).isEqualTo(NOW.toEpochMilli() + 1500);
        assertThat(result.status()).isEqualTo(PlaybackStatus.PLAYING);
        assertThat(result.anchorTime()).isEqualTo(NOW.toEpochMilli() + 1500);
        assertThat(result.version()).isEqualTo(3L);
        assertThat(result.serverTime()).isEqualTo(NOW.toEpochMilli());
        assertThat(result.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void apply_InvalidCommand_SavesNothing() {
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(stopped()));

        assertThatThrownBy(() -> playbackStateService.apply(sessionId,
                new SyncCommand(SyncCommandType.SEEK, "zero", hostId, null)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(playbackStateRepository, never()).saveAndFlush(any());
    }

    @Test
    void apply_WithoutState_IsNotFound() {
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playbackStateService.apply(sessionId,
                new SyncCommand(SyncCommandType.PLAY, null, hostId, null)))
                .isInstanceOf(PlaybackStateNotFoundException.class);
    }

    @Test
    void snapshot_MapsTheStateWithTheServerTime() {
        PlaybackState state = stopped();
        state.setVersion(7L);
        state.setMetronomeOn(true);
        when(playbackStateRepository.findById(sessionId)).thenReturn(Optional.of(state));

        PlaybackStateDto result = playbackStateService.snapshot(sessionId);

        assertThat(result).isEqualTo(new PlaybackStateDto(sessionId, 5, 1.5, 120, 6, 8, PlaybackStatus.STOPPED,
                1000L, true, 7L, NOW.toEpochMilli()));
    }

    @Test
    void snapshot_WithoutState_IsNotFound() {
        when(playbackStateRepository.findById(sessionId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> playbackStateService.snapshot(sessionId))
                .isInstanceOf(PlaybackStateNotFoundException.class);
    }

    private PlaybackState stopped() {
        return PlaybackState.builder()
                .sessionId(sessionId)
                .measure(5)
                .beat(1.5)
                .tempoBpm(120)
                .beatsPerMeasure(6)
                .beatUnit(8)
                .status(PlaybackStatus.STOPPED)
                .anchorTime(1000L)
                .build();
    }
}
