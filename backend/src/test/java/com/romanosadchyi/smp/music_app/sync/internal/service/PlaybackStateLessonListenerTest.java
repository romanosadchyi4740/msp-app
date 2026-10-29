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
class PlaybackStateLessonListenerTest {

    static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    PlaybackStateRepository playbackStateRepository;

    PlaybackStateLessonListener listener;

    UUID sessionId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new PlaybackStateLessonListener(playbackStateRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void lessonStarted_CreatesAStoppedStateAtTheFirstMeasureAndTheTabsTempo() {
        listener.lessonStarted(new LessonTab(sessionId, roomId, UUID.randomUUID(), 96));

        ArgumentCaptor<PlaybackState> saved = ArgumentCaptor.forClass(PlaybackState.class);
        verify(playbackStateRepository).save(saved.capture());
        PlaybackState state = saved.getValue();
        assertThat(state.getSessionId()).isEqualTo(sessionId);
        assertThat(state.getMeasure()).isEqualTo(1);
        assertThat(state.getBeat()).isZero();
        assertThat(state.getTempoBpm()).isEqualTo(96);
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getAnchorTime()).isEqualTo(NOW.toEpochMilli());
        assertThat(state.isMetronomeOn()).isFalse();
        assertThat(state.getVersion()).isNull();
    }

    @Test
    void tabChanged_RewindsTheExistingStateAndKeepsTheMetronome() {
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
        when(playbackStateRepository.findById(sessionId)).thenReturn(Optional.of(state));

        listener.tabChanged(new LessonTab(sessionId, roomId, UUID.randomUUID(), 70));

        verify(playbackStateRepository).save(state);
        assertThat(state.getMeasure()).isEqualTo(1);
        assertThat(state.getBeat()).isZero();
        assertThat(state.getTempoBpm()).isEqualTo(70);
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getAnchorTime()).isEqualTo(NOW.toEpochMilli());
        assertThat(state.isMetronomeOn()).isTrue();
    }

    @Test
    void lessonEnded_StopsWhereItIs() {
        PlaybackState state = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(9)
                .beat(1)
                .tempoBpm(100)
                .status(PlaybackStatus.PLAYING)
                .anchorTime(0)
                .build();
        when(playbackStateRepository.findById(sessionId)).thenReturn(Optional.of(state));

        listener.lessonEnded(sessionId);

        verify(playbackStateRepository).save(state);
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getMeasure()).isEqualTo(9);
        assertThat(state.getAnchorTime()).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void withoutState_ChangesNothing() {
        when(playbackStateRepository.findById(sessionId)).thenReturn(Optional.empty());

        listener.tabChanged(new LessonTab(sessionId, roomId, UUID.randomUUID(), 70));
        listener.lessonEnded(sessionId);

        verify(playbackStateRepository, never()).save(any());
    }
}
