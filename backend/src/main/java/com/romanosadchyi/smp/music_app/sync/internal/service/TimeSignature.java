package com.romanosadchyi.smp.music_app.sync.internal.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A time signature such as 4/4 or 6/8.
 *
 * @param beatsPerMeasure the upper number
 * @param beatUnit        the lower number, the note value of one beat
 */
public record TimeSignature(int beatsPerMeasure, int beatUnit) {

    public static final TimeSignature COMMON = new TimeSignature(4, 4);

    /**
     * The same form {@code tabs} accepts for a tab's time signature.
     */
    private static final Pattern FORMAT = Pattern.compile("([1-9]|[1-9][0-9])/(1|2|4|8|16|32)");

    /**
     * The tab's time signature, or {@link #COMMON} if it has none or one in another form.
     */
    public static TimeSignature parse(String value) {
        if (value == null) {
            return COMMON;
        }
        Matcher matcher = FORMAT.matcher(value.strip());
        if (!matcher.matches()) {
            return COMMON;
        }
        return new TimeSignature(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    /**
     * How many quarter beats one measure holds: 4 in 4/4, 3 in 3/4 and in 6/8.
     */
    public double quarterBeatsPerMeasure() {
        return beatsPerMeasure * 4.0 / beatUnit;
    }
}
