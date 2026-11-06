package com.romanosadchyi.smp.music_app.rooms.external;

import java.util.UUID;

/**
 * The shared playback of a lesson, kept by {@code sync}; {@code rooms} drives it through this interface so it never
 * depends on {@code sync}. Exactly one implementation exists, so the application does not start without it. Called in
 * the transaction that changes the lesson: if playback fails, the lesson change rolls back with it.
 */
public interface LessonPlayback {

    /**
     * Creates the lesson's playback, stopped at its first measure with the tab's tempo and time signature.
     */
    void start(LessonTab lesson);

    /**
     * The host put another tab on screen; playback starts over, stopped, with the new tab's tempo and time
     * signature.
     */
    void changeTab(LessonTab lesson);

    /**
     * The room got a new host without the old one stopping playback (the host left or stayed away); playing
     * playback pauses where it is, so the new host decides when it goes on. Anything else stays as it is.
     */
    void pause(UUID roomId, UUID sessionId);

    /**
     * The lesson ended; playback stops where it is.
     */
    void stop(UUID roomId, UUID sessionId);
}
