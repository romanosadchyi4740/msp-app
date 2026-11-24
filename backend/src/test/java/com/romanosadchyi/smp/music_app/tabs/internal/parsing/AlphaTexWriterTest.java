package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AlphaTexWriterTest {

    static final List<String> STANDARD = List.of("E4", "B3", "G3", "D3", "A2", "E2");

    AlphaTexWriter writer = new AlphaTexWriter();

    @Test
    void write_ScoreWithMetadataRepeatsChordsAndEffects_WritesAlphaTex() {
        AsciiMeasure riff = new AsciiMeasure(List.of(
                beat(note(3, 5, NoteEffect.HAMMER_PULL)),
                beat(note(3, 7)),
                beat(note(1, 0), note(6, 0))), true, 0);
        AsciiMeasure rest = new AsciiMeasure(List.of(), false, 2);
        AsciiMeasure lead = new AsciiMeasure(List.of(
                beat(new AsciiNote(4, null, Set.of(), 0)),
                beat(note(2, 3, NoteEffect.GHOST)),
                beat(new AsciiNote(2, 7, Set.of(NoteEffect.VIBRATO, NoteEffect.SLIDE), 4))), false, 0);

        String tex = writer.write(new AsciiTabScore(STANDARD, List.of(riff, rest, lead)), "Song \"live\"", "Band", 90,
                "3/4");

        assertThat(tex).isEqualTo("""
                \\title "Song live"
                \\artist "Band"
                \\track "Guitar"
                \\staff {tabs}
                \\tuning (E4 B3 G3 D3 A2 E2)
                \\ts (3 4) \\tempo 90 \\ro :4 5.3{h} 7.3 (0.1 0.6) |
                \\rc 2 :1 r |
                :4 x.4 3.2{g} 7.2{sl v b (0 4)} |
                """);
    }

    @Test
    void write_NoMetadata_WritesOnlyTheStaff() {
        AsciiMeasure measure = new AsciiMeasure(List.of(beat(note(1, 0))), false, 0);

        String tex = writer.write(new AsciiTabScore(STANDARD, List.of(measure)), null, null, null, null);

        assertThat(tex).isEqualTo("""
                \\track "Guitar"
                \\staff {tabs}
                \\tuning (E4 B3 G3 D3 A2 E2)
                :1 0.1 |
                """);
    }

    @Test
    void write_TimeSignatureOtherThanFourFour_FitsTheBeatsIntoThatBar() {
        AsciiMeasure fourBeats = new AsciiMeasure(List.of(
                beat(note(1, 0)), beat(note(1, 1)), beat(note(1, 3)), beat(note(1, 5))), false, 0);

        String tex = writer.write(new AsciiTabScore(STANDARD, List.of(fourBeats)), null, null, null, "3/4");

        assertThat(tex).endsWith("\\ts (3 4) :8 0.1 1.1 3.1 5.1 |\n");
    }

    @ParameterizedTest
    @CsvSource({
            "1,4,4,1", "2,4,4,2", "3,4,4,4", "4,4,4,4", "5,4,4,8", "8,4,4,8", "9,4,4,16", "17,4,4,32", "40,4,4,32",
            "3,3,4,4", "4,3,4,8", "6,6,8,8", "12,12,8,8", "2,2,2,2"})
    void duration_FitsTheBeatsIntoTheBar(int beats, int numerator, int denominator, int duration) {
        assertThat(AlphaTexWriter.duration(beats, numerator, denominator)).isEqualTo(duration);
    }

    private static AsciiBeat beat(AsciiNote... notes) {
        return new AsciiBeat(List.of(notes));
    }

    private static AsciiNote note(int string, int fret, NoteEffect... effects) {
        return new AsciiNote(string, fret, Set.of(effects), 0);
    }
}
