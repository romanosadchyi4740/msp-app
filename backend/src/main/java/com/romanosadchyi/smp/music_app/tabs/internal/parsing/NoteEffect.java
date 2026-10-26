package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

/**
 * Techniques written next to a fret in an ASCII tab.
 */
public enum NoteEffect {
    /** {@code 5h7}, {@code 7p5}: hammer-on or pull-off to the next note on the string. */
    HAMMER_PULL,
    /** {@code 5/7}, {@code 7\5}, {@code 5s7}. */
    SLIDE,
    /** {@code 5~}. */
    VIBRATO,
    /** {@code (5)}. */
    GHOST,
    /** {@code <12>}. */
    NATURAL_HARMONIC
}
