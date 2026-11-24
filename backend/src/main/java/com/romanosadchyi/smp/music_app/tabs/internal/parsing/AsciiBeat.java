package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import java.util.List;

/**
 * Notes played together: one column of the tab, or overlapping columns where a fret has two digits.
 */
public record AsciiBeat(List<AsciiNote> notes) {

    public AsciiBeat {
        notes = List.copyOf(notes);
    }
}
