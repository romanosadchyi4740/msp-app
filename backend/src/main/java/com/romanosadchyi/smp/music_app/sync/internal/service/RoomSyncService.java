package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.rooms.external.RoomLessonAccess;
import com.romanosadchyi.smp.music_app.shared.exception.NotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;
import com.romanosadchyi.smp.music_app.sync.internal.dto.PlaybackStateDto;
import com.romanosadchyi.smp.music_app.sync.internal.dto.RoomSyncSnapshot;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommandType;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * The sync channel of a room (lab 8, sub-process "Synchronization"): the host's commands change the lesson's playback,
 * which then goes to every participant, and any participant can ask for a snapshot (FR-8, FR-10). The hot path from
 * command to broadcast stays inside {@code sync}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RoomSyncService {

    RoomLessonAccess roomLessonAccess;
    PlaybackStateService playbackStateService;
    PlaybackNotifier playbackNotifier;
    Clock clock;

    /**
     * Applies a command from {@code senderId}, whose id replaces whatever sender the client claimed. A playback
     * command must come from the host; the room's row is locked before the playback's, and the new state goes to the
     * room after the commit. {@code REQUEST_SYNC} from any participant sends a snapshot to that participant only.
     *
     * @throws IllegalArgumentException   if the command has no type or an invalid argument
     * @throws NotFoundException          if there is no such room, the user is not in it, or it has no active lesson
     * @throws UserNotAuthorizedException if a participant other than the host sends a playback command
     */
    @Transactional
    public void handleCommand(UUID roomId, UUID senderId, SyncCommand received) {
        if (received == null || received.type() == null) {
            throw new IllegalArgumentException("A command type is required");
        }
        SyncCommand command = new SyncCommand(received.type(), received.argument(), senderId, received.clientTime());
        if (command.type() == SyncCommandType.REQUEST_SYNC) {
            playbackNotifier.sendSnapshot(senderId, snapshot(roomId, senderId));
            return;
        }
        UUID sessionId = roomLessonAccess.lockActiveLessonAsHost(roomId, senderId);
        PlaybackStateDto state = playbackStateService.apply(sessionId, command);
        log.debug("Host {} sent {} to room {}", senderId, command.type(), roomId);
        playbackNotifier.publishState(roomId, state);
    }

    /**
     * The room's lesson and playback as they are now.
     *
     * @throws NotFoundException if there is no such room or the user is not in it
     */
    @Transactional(readOnly = true)
    public RoomSyncSnapshot snapshot(UUID roomId, UUID userId) {
        return roomLessonAccess.findActiveLesson(roomId, userId)
                .map(sessionId -> {
                    PlaybackStateDto playback = playbackStateService.snapshot(sessionId);
                    return new RoomSyncSnapshot(roomId, sessionId, playback, playback.serverTime());
                })
                .orElseGet(() -> new RoomSyncSnapshot(roomId, null, null, clock.millis()));
    }
}
