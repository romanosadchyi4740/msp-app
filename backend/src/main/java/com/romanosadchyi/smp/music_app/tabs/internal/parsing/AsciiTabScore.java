package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import java.util.List;

/**
 * An ASCII tab parsed into measures. ASCII tabs carry no rhythm, so beats keep only their order.
 *
 * @param tuning the strings from the highest (top line) to the lowest, as alphaTex notes such as {@code E4}
 */
public record AsciiTabScore(List<String> tuning, List<AsciiMeasure> measures) {

    public AsciiTabScore {
        tuning = List.copyOf(tuning);
        measures = List.copyOf(measures);
    }
}
