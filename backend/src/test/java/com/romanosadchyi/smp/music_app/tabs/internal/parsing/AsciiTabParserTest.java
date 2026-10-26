package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Parses the tabs under {@code src/test/resources/tabs}: traditional and public-domain melodies and original riffs,
 * written the way tabs are found online.
 */
class AsciiTabParserTest {

    static final List<String> STANDARD = List.of("E4", "B3", "G3", "D3", "A2", "E2");

    AsciiTabParser parser = new AsciiTabParser();

    @Test
    void parse_MelodyWithTitleAndChordNames_ReadsEveryMeasureAcrossStaves() throws IOException {
        AsciiTabScore score = parser.parse(fixture("greensleeves.txt"));

        assertThat(score.tuning()).isEqualTo(STANDARD);
        assertThat(score.measures()).hasSize(8);
        assertThat(beatCounts(score)).containsExactly(3, 3, 3, 3, 2, 3, 3, 1);
        assertThat(score.measures().getFirst().beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("0.5", "1.2", "0.1");
        // A note on the low E string between two notes on higher strings keeps its column order
        assertThat(score.measures().get(2).beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("0.1", "3.6", "1.2");
        assertThat(describe(score.measures().get(4).beats().getFirst())).isEqualTo("0.1 0.6");
        assertThat(describe(score.measures().get(7).beats().getFirst())).isEqualTo("0.1 0.5");
        assertThat(score.measures()).noneMatch(AsciiMeasure::repeatOpen);
    }

    @Test
    void parse_Repeats_MarkTheOpeningAndClosingMeasuresWithTheCount() throws IOException {
        AsciiTabScore score = parser.parse(fixture("ode-to-joy.txt"));

        assertThat(score.measures()).hasSize(8);
        assertThat(beatCounts(score)).containsExactly(4, 4, 4, 3, 4, 4, 4, 3);
        assertThat(score.measures()).extracting(AsciiMeasure::repeatOpen)
                .containsExactly(true, false, false, false, false, false, false, false);
        assertThat(score.measures()).extracting(AsciiMeasure::repeatCount)
                .containsExactly(0, 0, 0, 3, 0, 0, 0, 0);
        assertThat(score.measures().getFirst().beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("0.1", "0.1", "1.1", "3.1");
    }

    @Test
    void parse_Techniques_AttachToTheirNotesAndDoNotBecomeNotes() throws IOException {
        AsciiTabScore score = parser.parse(fixture("techniques.txt"));

        assertThat(score.measures()).hasSize(2);
        List<AsciiBeat> riff = score.measures().getFirst().beats();
        assertThat(riff).extracting(AsciiTabParserTest::describe).containsExactly(
                "5.3{HAMMER_PULL}", "7.3", "7.3{HAMMER_PULL}", "5.3", "7.3{SLIDE} 7.4", "9.3", "9.3{SLIDE}",
                "7.3 x.4");

        List<AsciiBeat> lead = score.measures().get(1).beats();
        assertThat(lead).extracting(AsciiTabParserTest::describe)
                .containsExactly("8.2 b4", "8.2{GHOST}", "12.2{NATURAL_HARMONIC}", "12.4{VIBRATO}");
    }

    @Test
    void parse_RaggedLinesAndAStaffWithoutItsLowString_KeepsEveryNote() throws IOException {
        AsciiTabScore score = parser.parse(fixture("incomplete.txt"));

        assertThat(score.tuning()).isEqualTo(STANDARD);
        assertThat(beatCounts(score)).containsExactly(6, 3, 1, 1);
        // Notes after the last bar line of the longer lines form a measure of their own
        assertThat(score.measures().get(1).beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("2.4", "2.3", "1.2");
        assertThat(describe(score.measures().get(2).beats().getFirst())).isEqualTo("0.1 1.2 0.3 2.4 3.5");
        assertThat(describe(score.measures().get(3).beats().getFirst())).isEqualTo("3.1 0.2 0.3 0.4 2.5");
    }

    @Test
    void parse_StringNames_SetTheTuning() throws IOException {
        AsciiTabScore score = parser.parse(fixture("drop-d.txt"));

        assertThat(score.tuning()).containsExactly("E4", "B3", "G3", "D3", "A2", "D2");
        assertThat(score.measures().getFirst().beats()).hasSize(6)
                .allSatisfy(beat -> assertThat(beat.notes()).extracting(AsciiNote::string).containsExactly(4, 5, 6));
    }

    @Test
    void parse_FourStringBass_UsesTheBassTuning() throws IOException {
        AsciiTabScore score = parser.parse(fixture("bass.txt"));

        assertThat(score.tuning()).containsExactly("G2", "D2", "A1", "E1");
        assertThat(score.measures().getFirst().beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("0.3", "3.4", "0.3", "3.4", "3.3", "5.4", "3.3", "5.4");
    }

    @Test
    void parse_UnnamedLinesAndTwelveLinesWithoutABlankLine_AreTwoGuitarStaves() {
        String staff = "|-0-|\n".repeat(6);

        AsciiTabScore score = parser.parse(staff + staff);

        assertThat(score.tuning()).isEqualTo(STANDARD);
        assertThat(beatCounts(score)).containsExactly(1, 1);
        assertThat(score.measures().getFirst().beats().getFirst().notes()).hasSize(6);
    }

    @Test
    void parse_TwoDigitFrets_LineUpWithOneDigitFretsOnOtherStrings() {
        String tab = """
                e|-12-----|
                B|--3--10-|
                G|-----9--|
                D|--------|
                A|--------|
                E|--------|
                """;

        assertThat(parser.parse(tab).measures().getFirst().beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("12.1 3.2", "10.2 9.3");
    }

    @Test
    void parse_NotesAfterTheLines_GiveRepeatCountsAndDoNotBecomeNotes() throws IOException {
        AsciiTabScore score = parser.parse(fixture("annotations.txt"));

        assertThat(beatCounts(score)).containsExactly(1, 1, 1, 1, 1, 1);
        assertThat(score.measures()).extracting(AsciiMeasure::repeatCount).containsExactly(0, 3, 0, 4, 0, 2);
        // "<- next part" has an x, which would otherwise be a dead note
        assertThat(score.measures().getFirst().beats().getFirst().notes()).hasSize(5)
                .noneMatch(note -> note.fret() == null);
    }

    @Test
    void parse_RepeatDotsOnlyOnTheMiddleStrings_MarkTheRepeat() throws IOException {
        AsciiTabScore score = parser.parse(fixture("repeat-dots.txt"));

        assertThat(beatCounts(score)).containsExactly(2, 2);
        assertThat(score.measures()).extracting(AsciiMeasure::repeatOpen).containsExactly(true, false);
        assertThat(score.measures()).extracting(AsciiMeasure::repeatCount).containsExactly(0, 2);
        assertThat(score.measures().getFirst().beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("0.3", "2.4");
    }

    @Test
    void parse_RepeatOpenedAtTheEndOfAStaff_StartsTheNextStaff() {
        String tab = "|--0--||:\n".repeat(4) + "\n" + "|--3--:||\n".repeat(4);

        AsciiTabScore score = parser.parse(tab);

        assertThat(score.measures()).extracting(AsciiMeasure::repeatOpen).containsExactly(false, true);
        assertThat(score.measures()).extracting(AsciiMeasure::repeatCount).containsExactly(0, 2);
    }

    @Test
    void parse_TwoBassStavesWithoutABlankLine_AreSplitWhereTheNamesStartOver() {
        String tab = """
                G|-----|
                D|-----|
                A|-0---|
                E|---3-|
                G|-----|
                D|-2---|
                A|-----|
                E|---0-|
                """;

        AsciiTabScore score = parser.parse(tab);

        assertThat(score.tuning()).containsExactly("G2", "D2", "A1", "E1");
        assertThat(score.measures()).hasSize(2);
        assertThat(score.measures().get(1).beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("2.2", "0.4");
    }

    @Test
    void parse_OpenTuningWithRepeatedStringNames_StaysOneStaff() {
        String tab = """
                D|-0-|
                A|-0-|
                F#|-0-|
                D|-0-|
                A|-0-|
                D|-0-|
                """;

        AsciiTabScore score = parser.parse(tab);

        assertThat(score.tuning()).containsExactly("D4", "A3", "F#3", "D3", "A2", "D2");
        assertThat(score.measures()).hasSize(1);
        assertThat(score.measures().getFirst().beats().getFirst().notes()).hasSize(6);
    }

    @Test
    void parse_GuitarStaffWithoutItsLowStrings_KeepsTheGuitarTuning() {
        String tab = "e|-0-|\nB|-1-|\nG|-0-|\nD|-2-|\nA|-3-|\n";

        AsciiTabScore score = parser.parse(tab);

        assertThat(score.tuning()).isEqualTo(STANDARD);
        assertThat(describe(score.measures().getFirst().beats().getFirst())).isEqualTo("0.1 1.2 0.3 2.4 3.5");
    }

    @Test
    void parse_BendTargetInParenthesesAndVibratoAfterIt_AreOneNote() {
        String tab = "e|---------|\nB|-7b(9)~~-|\nG|---------|\nD|---------|\n";

        assertThat(parser.parse(tab).measures().getFirst().beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("7.2{VIBRATO} b4");
    }

    @Test
    void parse_SecondNoteOnAStringWithinOverlappingColumns_StartsTheNextBeat() {
        String tab = "e|-1012-|\nB|--11--|\nG|------|\nD|------|\n";

        assertThat(parser.parse(tab).measures().getFirst().beats()).extracting(AsciiTabParserTest::describe)
                .containsExactly("10.1 11.2", "12.1");
    }

    @Test
    void parse_EmptyMeasure_IsAMeasureWithoutBeats() {
        String tab = "|-0-|---|\n|---|---|\n|---|---|\n|---|---|\n";

        AsciiTabScore score = parser.parse(tab);

        assertThat(beatCounts(score)).containsExactly(1, 0);
        assertThat(score.tuning()).containsExactly("G2", "D2", "A1", "E1");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Just some lyrics\nand chords: Am C G",
            "e|---0---|\nB|---1---|\nG|---0---|",
            "|  Am  |  C  |\n|  G  |  F  |\n|  Am  |  C  |\n|  G  |  F  |",
            ""})
    void parse_NoStaffOfAtLeastFourLines_Rejects(String text) {
        assertThatThrownBy(() -> parser.parse(text))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("No tab found");
    }

    @Test
    void parse_StaffWithoutNotesOrBars_Rejects() {
        assertThatThrownBy(() -> parser.parse("e|------\nB|------\nG|------\nD|------\n"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The tab has no measures");
    }

    private static List<Integer> beatCounts(AsciiTabScore score) {
        return score.measures().stream().map(measure -> measure.beats().size()).toList();
    }

    /**
     * A beat as {@code fret.string{EFFECTS}} per note, separated by spaces; a bend adds {@code b<quarter tones>}.
     */
    private static String describe(AsciiBeat beat) {
        return String.join(" ", beat.notes().stream().map(AsciiTabParserTest::describe).toList());
    }

    private static String describe(AsciiNote note) {
        String fret = note.fret() != null ? note.fret().toString() : "x";
        Set<NoteEffect> effects = note.effects();
        String effectText = effects.isEmpty() ? "" : effects.stream().map(Enum::name).sorted()
                .toList().toString().replace('[', '{').replace(']', '}').replace(", ", ",");
        return fret + "." + note.string() + effectText + (note.bendQuarterTones() > 0 ? " b" + note.bendQuarterTones()
                : "");
    }

    private static String fixture(String name) throws IOException {
        try (InputStream in = AsciiTabParserTest.class.getResourceAsStream("/tabs/" + name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
