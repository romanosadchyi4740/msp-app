package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import java.util.Set;

/**
 * One note of an ASCII tab.
 *
 * @param string           1 for the top line of the staff, which is the highest string
 * @param fret             the fret, or {@code null} for a dead note ({@code x})
 * @param bendQuarterTones how far the note is bent ({@code 7b9} is 4 quarter tones), 0 if not
 */
public record AsciiNote(int string, Integer fret, Set<NoteEffect> effects, int bendQuarterTones) {

    public AsciiNote {
        effects = Set.copyOf(effects);
    }
}
