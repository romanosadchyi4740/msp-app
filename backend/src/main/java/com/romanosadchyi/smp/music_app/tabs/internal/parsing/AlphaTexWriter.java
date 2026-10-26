package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Writes a parsed ASCII tab as alphaTex (alphaTab 1.7+ syntax, without section dots) for the tab canvas: one guitar
 * track shown as tablature, one alphaTex bar per measure. In {@code fret.string}, string 1 is the highest string,
 * and {@code \tuning} lists the strings from the highest.
 * <p>
 * ASCII tabs have no rhythm, so every beat of a measure gets the same duration: the longest note value that fits
 * the beats into the bar (in 4/4, 4 beats are quarters and 5 to 8 are eighths; in 3/4, 3 beats are quarters), at most
 * a 32nd, so a bar with more beats than 32nds fit is written overfull. Without a time signature the bar is 4/4.
 */
@Component
public class AlphaTexWriter {

    static final int SHORTEST_DURATION = 32;

    /**
     * @param title         shown above the tab; may be {@code null}
     * @param artist        may be {@code null}
     * @param tempoBpm      may be {@code null}
     * @param timeSignature such as {@code 4/4}; may be {@code null}
     */
    public String write(AsciiTabScore score, String title, String artist, Integer tempoBpm, String timeSignature) {
        StringBuilder tex = new StringBuilder();
        if (title != null) {
            tex.append("\\title ").append(quote(title)).append('\n');
        }
        if (artist != null) {
            tex.append("\\artist ").append(quote(artist)).append('\n');
        }
        tex.append("\\track \"Guitar\"\n\\staff {tabs}\n\\tuning (")
                .append(String.join(" ", score.tuning()))
                .append(")\n");

        int[] signature = timeSignature != null ? parseTimeSignature(timeSignature) : new int[]{4, 4};
        List<String> bars = new ArrayList<>();
        for (int i = 0; i < score.measures().size(); i++) {
            StringBuilder bar = new StringBuilder();
            if (i == 0 && timeSignature != null) {
                bar.append("\\ts (").append(signature[0]).append(' ').append(signature[1]).append(") ");
            }
            if (i == 0 && tempoBpm != null) {
                bar.append("\\tempo ").append(tempoBpm).append(' ');
            }
            bar.append(measure(score.measures().get(i), signature));
            bars.add(bar.toString());
        }
        tex.append(String.join(" |\n", bars)).append(" |\n");
        return tex.toString();
    }

    private static String measure(AsciiMeasure measure, int[] signature) {
        StringBuilder bar = new StringBuilder();
        if (measure.repeatOpen()) {
            bar.append("\\ro ");
        }
        if (measure.repeatCount() > 0) {
            bar.append("\\rc ").append(measure.repeatCount()).append(' ');
        }
        if (measure.beats().isEmpty()) {
            return bar.append(":1 r").toString();
        }
        bar.append(':').append(duration(measure.beats().size(), signature[0], signature[1]));
        for (AsciiBeat beat : measure.beats()) {
            bar.append(' ').append(beat(beat));
        }
        return bar.toString();
    }

    /**
     * The longest power-of-two note value of which {@code beats} fit into a bar of {@code numerator / denominator}.
     */
    static int duration(int beats, int numerator, int denominator) {
        int duration = 1;
        while ((long) beats * denominator > (long) numerator * duration && duration < SHORTEST_DURATION) {
            duration *= 2;
        }
        return duration;
    }

    /**
     * {@code {numerator, denominator}} of a signature such as {@code 6/8}, as checked by the tab validator.
     */
    private static int[] parseTimeSignature(String timeSignature) {
        String[] parts = timeSignature.split("/");
        return new int[]{Integer.parseInt(parts[0]), Integer.parseInt(parts[1])};
    }

    private static String beat(AsciiBeat beat) {
        if (beat.notes().size() == 1) {
            return note(beat.notes().getFirst());
        }
        StringJoiner chord = new StringJoiner(" ", "(", ")");
        beat.notes().forEach(note -> chord.add(note(note)));
        return chord.toString();
    }

    private static String note(AsciiNote note) {
        String value = (note.fret() != null ? note.fret().toString() : "x") + "." + note.string();
        StringJoiner effects = new StringJoiner(" ", "{", "}").setEmptyValue("");
        for (NoteEffect effect : NoteEffect.values()) {
            if (!note.effects().contains(effect)) {
                continue;
            }
            effects.add(switch (effect) {
                case HAMMER_PULL -> "h";
                case SLIDE -> "sl";
                case VIBRATO -> "v";
                case GHOST -> "g";
                case NATURAL_HARMONIC -> "nh";
            });
        }
        if (note.bendQuarterTones() > 0) {
            effects.add("b (0 " + note.bendQuarterTones() + ")");
        }
        return value + effects;
    }

    /**
     * An alphaTex string literal; double quotes and backslashes inside are left out rather than escaped.
     */
    private static String quote(String text) {
        return "\"" + text.replace("\"", "").replace("\\", "") + "\"";
    }
}
