package com.romanosadchyi.smp.music_app.rooms.external;

import com.romanosadchyi.smp.music_app.shared.exception.NotFoundException;
import com.romanosadchyi.smp.music_app.shared.exception.UserNotAuthorizedException;

import java.util.Optional;
import java.util.UUID;

/**
 * Who may drive a room's lesson, for the sync channel in {@code sync} (FR-8): only the room's host sends playback
 * commands, and any participant reads the lesson. A room the user is not in answers as not found.
 */
public interface RoomLessonAccess {

    /**
     * Locks the room's row until the caller's transaction ends, then checks the user is its host and returns the
     * active lesson. The room is locked before the caller locks the lesson's playback, the same order as lesson
     * changes in {@code rooms}, so the two cannot deadlock, and a host hand-over cannot slip in between the check and
     * the command. Runs only inside the caller's transaction.
     *
     * @throws NotFoundException          if there is no such room, the user is not in it, or it has no active lesson
     * @throws UserNotAuthorizedException if the user is not the host
     */
    UUID lockActiveLessonAsHost(UUID roomId, UUID userId);

    /**
     * The room's active lesson; empty if it has none.
     *
     * @throws NotFoundException if there is no such room or the user is not in it
     */
    Optional<UUID> findActiveLesson(UUID roomId, UUID userId);
}
