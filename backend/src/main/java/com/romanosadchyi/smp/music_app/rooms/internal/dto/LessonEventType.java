package com.romanosadchyi.smp.music_app.rooms.internal.dto;

public enum LessonEventType {
    LESSON_STARTED,
    TAB_CHANGED,
    /**
     * The limit ends the lesson in 5 minutes or less.
     */
    LIMIT_WARNING,
    LESSON_ENDED
}
