package com.romanosadchyi.smp.music_app.rooms.external;

import java.util.UUID;

/**
 * Implemented by modules that keep per-lesson data, such as the shared playback state of {@code sync}, so
 * {@code rooms} never depends on them. Called in the transaction that changes the lesson.
 */
public interface LessonListener {

    void lessonStarted(LessonTab lesson);

    /**
     * The host put another tab on screen; playback starts over from its beginning.
     */
    void tabChanged(LessonTab lesson);

    void lessonEnded(UUID sessionId);
}
