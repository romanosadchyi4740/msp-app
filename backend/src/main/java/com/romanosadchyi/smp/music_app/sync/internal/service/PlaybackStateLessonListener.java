package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.LessonListener;
import com.romanosadchyi.smp.music_app.rooms.external.LessonTab;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import com.romanosadchyi.smp.music_app.sync.internal.repository.PlaybackStateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.UUID;

/**
 * Keeps one playback state per lesson: a new lesson and a new tab start stopped at the first measure and the tab's
 * tempo, and an ended lesson stops. The state is deleted with its lesson, by the database.
 */
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class PlaybackStateLessonListener implements LessonListener {

    static final int FIRST_MEASURE = 1;

    PlaybackStateRepository playbackStateRepository;
    Clock clock;

    @Override
    public void lessonStarted(LessonTab lesson) {
        PlaybackState state = PlaybackState.builder()
                .sessionId(lesson.sessionId())
                .metronomeOn(false)
                .build();
        playbackStateRepository.save(rewind(state, lesson.tempoBpm()));
    }

    /**
     * Keeps the row, so its version grows and clients take the reset as newer than what they had.
     */
    @Override
    public void tabChanged(LessonTab lesson) {
        playbackStateRepository.findById(lesson.sessionId())
                .ifPresent(state -> playbackStateRepository.save(rewind(state, lesson.tempoBpm())));
    }

    @Override
    public void lessonEnded(UUID sessionId) {
        playbackStateRepository.findById(sessionId).ifPresent(state -> {
            state.setStatus(PlaybackStatus.STOPPED);
            state.setAnchorTime(clock.millis());
            playbackStateRepository.save(state);
        });
    }

    private PlaybackState rewind(PlaybackState state, int tempoBpm) {
        state.setMeasure(FIRST_MEASURE);
        state.setBeat(0);
        state.setTempoBpm(tempoBpm);
        state.setStatus(PlaybackStatus.STOPPED);
        state.setAnchorTime(clock.millis());
        return state;
    }
}
