package com.romanosadchyi.smp.music_app.rooms.external;

import java.util.UUID;

/**
 * The shared playback of a lesson, kept by {@code sync}; {@code rooms} drives it through this interface so it never
 * depends on {@code sync}. Exactly one implementation exists, so the application does not start without it. Called in
 * the transaction that changes the lesson: if playback fails, the lesson change rolls back with it.
 */
public interface LessonPlayback {

    /**
     * Creates the lesson's playback, stopped at its first measure and the tab's tempo.
     */
    void start(LessonTab lesson);

    /**
     * The host put another tab on screen; playback starts over, stopped, at the new tab's tempo.
     */
    void changeTab(LessonTab lesson);

    /**
     * The lesson ended; playback stops where it is.
     */
    void stop(UUID sessionId);
}
