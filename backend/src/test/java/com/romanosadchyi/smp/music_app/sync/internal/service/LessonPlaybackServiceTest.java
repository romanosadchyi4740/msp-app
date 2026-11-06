package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.LessonTab;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import com.romanosadchyi.smp.music_app.sync.internal.repository.PlaybackStateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonPlaybackServiceTest {

    static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    PlaybackStateRepository playbackStateRepository;

    LessonPlaybackService lessonPlayback;

    UUID sessionId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lessonPlayback = new LessonPlaybackService(playbackStateRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void start_CreatesAStoppedStateAtTheFirstMeasureWithTheTabsTempoAndTimeSignature() {
        lessonPlayback.start(new LessonTab(sessionId, roomId, UUID.randomUUID(), 96, "6/8"));

        ArgumentCaptor<PlaybackState> saved = ArgumentCaptor.forClass(PlaybackState.class);
        verify(playbackStateRepository).save(saved.capture());
        PlaybackState state = saved.getValue();
        assertThat(state.getSessionId()).isEqualTo(sessionId);
        assertThat(state.getMeasure()).isEqualTo(1);
        assertThat(state.getBeat()).isZero();
        assertThat(state.getTempoBpm()).isEqualTo(96);
        assertThat(state.getBeatsPerMeasure()).isEqualTo(6);
        assertThat(state.getBeatUnit()).isEqualTo(8);
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getAnchorTime()).isEqualTo(NOW.toEpochMilli());
        assertThat(state.isMetronomeOn()).isFalse();
        assertThat(state.getVersion()).isNull();
    }

    @Test
    void start_TabWithoutTimeSignature_UsesFourFour() {
        lessonPlayback.start(new LessonTab(sessionId, roomId, UUID.randomUUID(), 96, null));

        ArgumentCaptor<PlaybackState> saved = ArgumentCaptor.forClass(PlaybackState.class);
        verify(playbackStateRepository).save(saved.capture());
        assertThat(saved.getValue().getBeatsPerMeasure()).isEqualTo(4);
        assertThat(saved.getValue().getBeatUnit()).isEqualTo(4);
    }

    @Test
    void changeTab_RewindsTheExistingStateAndKeepsTheMetronome() {
        PlaybackState state = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(17)
                .beat(2.5)
                .tempoBpm(120)
                .status(PlaybackStatus.PLAYING)
                .anchorTime(0)
                .metronomeOn(true)
                .version(4L)
                .build();
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(state));

        lessonPlayback.changeTab(new LessonTab(sessionId, roomId, UUID.randomUUID(), 70, "3/4"));

        verify(playbackStateRepository).save(state);
        assertThat(state.getMeasure()).isEqualTo(1);
        assertThat(state.getBeat()).isZero();
        assertThat(state.getTempoBpm()).isEqualTo(70);
        assertThat(state.getBeatsPerMeasure()).isEqualTo(3);
        assertThat(state.getBeatUnit()).isEqualTo(4);
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getAnchorTime()).isEqualTo(NOW.toEpochMilli());
        assertThat(state.isMetronomeOn()).isTrue();
    }

    @Test
    void stop_PlayingState_StopsWhereItIsNow() {
        // 120 BPM: 2 quarter beats per second, so 3 s after the anchor at measure 9 beat 1 it is at measure 10 beat 3
        PlaybackState state = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(9)
                .beat(1)
                .tempoBpm(120)
                .status(PlaybackStatus.PLAYING)
                .anchorTime(NOW.toEpochMilli() - 3000)
                .build();
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(state));

        lessonPlayback.stop(sessionId);

        verify(playbackStateRepository).save(state);
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getMeasure()).isEqualTo(10);
        assertThat(state.getBeat()).isEqualTo(3.0);
        assertThat(state.getAnchorTime()).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void stop_PausedState_KeepsItsPosition() {
        PlaybackState state = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(9)
                .beat(1)
                .tempoBpm(100)
                .status(PlaybackStatus.PAUSED)
                .anchorTime(0)
                .build();
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(state));

        lessonPlayback.stop(sessionId);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getMeasure()).isEqualTo(9);
        assertThat(state.getBeat()).isEqualTo(1.0);
        assertThat(state.getAnchorTime()).isZero();
    }

    @Test
    void withoutState_ChangesNothing() {
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.empty());

        lessonPlayback.changeTab(new LessonTab(sessionId, roomId, UUID.randomUUID(), 70, "4/4"));
        lessonPlayback.stop(sessionId);

        verify(playbackStateRepository, never()).save(any());
    }
}
