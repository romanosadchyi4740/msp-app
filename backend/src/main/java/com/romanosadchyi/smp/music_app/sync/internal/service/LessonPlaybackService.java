package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.LessonPlayback;
import com.romanosadchyi.smp.music_app.rooms.external.LessonTab;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.mapping.PlaybackStateMappingService;
import com.romanosadchyi.smp.music_app.sync.internal.repository.PlaybackStateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.UUID;

/**
 * Keeps one playback state per lesson: a new lesson and a new tab start stopped at the first measure with the tab's
 * tempo and time signature, and an ended lesson stops where it is. Every change goes to the room after the commit,
 * like a host command, so the state topic always carries the newest version. The state is deleted with its lesson,
 * by the database.
 */
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
class LessonPlaybackService implements LessonPlayback {

    PlaybackStateRepository playbackStateRepository;
    PlaybackStateMappingService playbackStateMappingService;
    PlaybackNotifier playbackNotifier;
    Clock clock;

    @Override
    public void start(LessonTab lesson) {
        PlaybackState state = PlaybackState.builder()
                .sessionId(lesson.sessionId())
                .metronomeOn(false)
                .build();
        loadTab(state, lesson);
        saveAndPublish(lesson.roomId(), state);
    }

    /**
     * Keeps the row, so its version grows and clients take the reset as newer than what they had.
     */
    @Override
    public void changeTab(LessonTab lesson) {
        playbackStateRepository.findByIdForUpdate(lesson.sessionId()).ifPresent(state -> {
            loadTab(state, lesson);
            saveAndPublish(lesson.roomId(), state);
        });
    }

    @Override
    public void stop(UUID roomId, UUID sessionId) {
        playbackStateRepository.findByIdForUpdate(sessionId).ifPresent(state -> {
            PlaybackStates.stopAt(state, clock.millis());
            saveAndPublish(roomId, state);
        });
    }

    private void loadTab(PlaybackState state, LessonTab lesson) {
        PlaybackStates.loadTab(state, lesson.tempoBpm(), TimeSignature.parse(lesson.timeSignature()), clock.millis());
    }

    /**
     * Flushes first, so the published state carries the version the row now has.
     */
    private void saveAndPublish(UUID roomId, PlaybackState state) {
        PlaybackState saved = playbackStateRepository.saveAndFlush(state);
        playbackNotifier.publishState(roomId, playbackStateMappingService.map(saved, clock.millis()));
    }
}
