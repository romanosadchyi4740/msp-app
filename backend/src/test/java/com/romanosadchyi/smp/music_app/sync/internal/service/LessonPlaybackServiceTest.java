package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.LessonTab;
import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import com.romanosadchyi.smp.music_app.sync.internal.mapping.PlaybackStateMappingService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LessonPlaybackServiceTest {

    static final Instant NOW = Instant.parse("2026-10-29T10:00:00Z");

    @Mock
    PlaybackStateRepository playbackStateRepository;

    @Mock
    PlaybackNotifier playbackNotifier;

    LessonPlaybackService lessonPlayback;

    UUID sessionId = UUID.randomUUID();
    UUID roomId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lessonPlayback = new LessonPlaybackService(playbackStateRepository, new PlaybackStateMappingService(),
                playbackNotifier, Clock.fixed(NOW, ZoneOffset.UTC));
        lenient().when(playbackStateRepository.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void start_CreatesAStoppedStateAtTheFirstMeasureWithTheTabsTempoAndTimeSignature() {
        lessonPlayback.start(new LessonTab(sessionId, roomId, UUID.randomUUID(), 96, "6/8"));

        ArgumentCaptor<PlaybackState> saved = ArgumentCaptor.forClass(PlaybackState.class);
        verify(playbackStateRepository).saveAndFlush(saved.capture());
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
        verify(playbackStateRepository).saveAndFlush(saved.capture());
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

        verify(playbackStateRepository).saveAndFlush(state);
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
        // 120 BPM: 2 quarter beats per second, so 3 s after the anchor at measure 9 beat 1 it is 7 quarter beats into
        // measure 9 (positions are not wrapped on the server, KAN-95)
        PlaybackState state = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(9)
                .beat(1)
                .tempoBpm(120)
                .status(PlaybackStatus.PLAYING)
                .anchorTime(NOW.toEpochMilli() - 3000)
                .build();
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(state));

        lessonPlayback.stop(roomId, sessionId);

        verify(playbackStateRepository).saveAndFlush(state);
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getMeasure()).isEqualTo(9);
        assertThat(state.getBeat()).isEqualTo(7.0);
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

        lessonPlayback.stop(roomId, sessionId);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getMeasure()).isEqualTo(9);
        assertThat(state.getBeat()).isEqualTo(1.0);
        assertThat(state.getAnchorTime()).isZero();
    }

    @Test
    void changes_PublishTheSavedStateToTheRoom() {
        PlaybackState state = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(3)
                .tempoBpm(100)
                .status(PlaybackStatus.PLAYING)
                .anchorTime(NOW.toEpochMilli())
                .build();
        PlaybackState flushed = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(3)
                .tempoBpm(100)
                .status(PlaybackStatus.STOPPED)
                .anchorTime(NOW.toEpochMilli())
                .version(5L)
                .build();
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(state));
        when(playbackStateRepository.saveAndFlush(state)).thenReturn(flushed);

        lessonPlayback.stop(roomId, sessionId);

        ArgumentCaptor<PlaybackStateDto> published = ArgumentCaptor.forClass(PlaybackStateDto.class);
        verify(playbackNotifier).publishState(eq(roomId), published.capture());
        assertThat(published.getValue().version()).isEqualTo(5L);
        assertThat(published.getValue().status()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(published.getValue().serverTime()).isEqualTo(NOW.toEpochMilli());
    }

    @Test
    void pause_PlayingState_PausesWhereItIsNowAndPublishes() {
        // 120 BPM: 2 quarter beats per second, so 1.5 s after the anchor at measure 2 beat 0 it is at beat 3
        PlaybackState state = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(2)
                .beat(0)
                .tempoBpm(120)
                .status(PlaybackStatus.PLAYING)
                .anchorTime(NOW.toEpochMilli() - 1500)
                .build();
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(state));

        lessonPlayback.pause(roomId, sessionId);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.PAUSED);
        assertThat(state.getMeasure()).isEqualTo(2);
        assertThat(state.getBeat()).isEqualTo(3.0);
        verify(playbackStateRepository).saveAndFlush(state);
        verify(playbackNotifier).publishState(eq(roomId), any());
    }

    @Test
    void pause_NotPlaying_ChangesAndPublishesNothing() {
        PlaybackState state = PlaybackState.builder()
                .sessionId(sessionId)
                .measure(2)
                .tempoBpm(120)
                .status(PlaybackStatus.STOPPED)
                .build();
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(state));

        lessonPlayback.pause(roomId, sessionId);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        verify(playbackStateRepository, never()).saveAndFlush(any());
        verify(playbackNotifier, never()).publishState(any(), any());
    }

    @Test
    void start_PublishesToTheLessonsRoom() {
        lessonPlayback.start(new LessonTab(sessionId, roomId, UUID.randomUUID(), 96, "4/4"));

        verify(playbackNotifier).publishState(eq(roomId), any());
    }

    @Test
    void withoutState_ChangesNothing() {
        when(playbackStateRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.empty());

        lessonPlayback.changeTab(new LessonTab(sessionId, roomId, UUID.randomUUID(), 70, "4/4"));
        lessonPlayback.stop(roomId, sessionId);

        verify(playbackStateRepository, never()).saveAndFlush(any());
        verify(playbackNotifier, never()).publishState(any(), any());
    }
}
