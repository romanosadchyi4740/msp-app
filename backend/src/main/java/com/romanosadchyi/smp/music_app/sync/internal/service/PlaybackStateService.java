package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.exception.PlaybackStateNotFoundException;
import com.romanosadchyi.smp.music_app.sync.internal.mapping.PlaybackStateMappingService;
import com.romanosadchyi.smp.music_app.sync.internal.repository.PlaybackStateRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Applies commands to a lesson's playback and takes snapshots of it. Who may send a command is checked by the caller.
 * Every writer of the state (these commands and the lesson changes through {@code LessonPlayback}) locks its row, so
 * changes to one lesson apply one after another, each at its own server time, and none fails on the version. Lock
 * order: a caller that also locks the room's row locks it first, as {@code rooms} does for lesson changes, so the
 * two paths cannot deadlock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PlaybackStateService {

    PlaybackStateRepository playbackStateRepository;
    PlaybackStateMappingService playbackStateMappingService;
    Clock clock;

    /**
     * Applies the command at the current server time and returns the new state; a command that changes nothing
     * returns the state with the same version.
     *
     * @throws IllegalArgumentException       if the command is invalid or is {@code REQUEST_SYNC}
     * @throws PlaybackStateNotFoundException if the lesson has no playback
     */
    @Transactional
    public PlaybackStateDto apply(UUID sessionId, SyncCommand command) {
        PlaybackState state = playbackStateRepository.findByIdForUpdate(sessionId).orElseThrow(this::notFound);
        long serverNow = clock.millis();
        PlaybackStates.apply(state, command, serverNow);
        PlaybackState saved = playbackStateRepository.saveAndFlush(state);
        log.debug("Applied {} to the playback of lesson {}", command.type(), sessionId);
        return playbackStateMappingService.map(saved, serverNow);
    }

    /**
     * The state as it is now, for a participant who joins, reconnects or asks to sync.
     *
     * @throws PlaybackStateNotFoundException if the lesson has no playback
     */
    @Transactional(readOnly = true)
    public PlaybackStateDto snapshot(UUID sessionId) {
        PlaybackState state = playbackStateRepository.findById(sessionId).orElseThrow(this::notFound);
        return playbackStateMappingService.map(state, clock.millis());
    }

    private PlaybackStateNotFoundException notFound() {
        return new PlaybackStateNotFoundException("The lesson has no playback");
    }
}
