package com.romanosadchyi.smp.music_app.sync.internal.dto;

public enum SyncCommandType {
    PLAY,
    PAUSE,
    /**
     * Argument: the measure to jump to, 1-based.
     */
    SEEK,
    /**
     * Argument: the new tempo in quarter notes per minute.
     */
    SET_TEMPO,
    /**
     * Argument: {@code true} or {@code false}.
     */
    METRONOME,
    /**
     * Back to the first measure of the tab on screen, stopped. Putting another tab on screen is a lesson change in
     * {@code rooms}, which rewinds the playback the same way.
     */
    LOAD_TAB,
    /**
     * Asks for a snapshot; changes nothing.
     */
    REQUEST_SYNC
}
