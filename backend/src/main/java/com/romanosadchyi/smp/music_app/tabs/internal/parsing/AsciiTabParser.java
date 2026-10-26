package com.romanosadchyi.smp.music_app.tabs.internal.parsing;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a plain-text tab into measures (the lab 7 {@code Tab.parse()} for ASCII tabs).
 * <p>
 * A staff is a run of lines such as {@code e|---0---|}: an optional string name, a {@code |}, then the tab. Other
 * lines (titles, chord names, lyrics) end a staff and are ignored; staves written without a blank line between them
 * are split where the string names start over. Bar lines are the columns where most lines of the staff have a
 * {@code |} or repeat dots ({@code :} or {@code *}); dots next to a bar line, even on only some strings, mark a repeat,
 * and a note such as {@code x3} after a line gives its count. Notes in overlapping columns form one beat, so a
 * two-digit fret lines up with one-digit frets on other strings. Lines of a staff may differ in length and a staff
 * may lack its bottom lines.
 * <p>
 * A tab with 6 to 8 lines, or whose top string is named {@code e}, is a guitar tab; one with 4 or 5 other lines is a
 * bass tab.
 */
@Component
public class AsciiTabParser {

    static final int MIN_STRINGS = 4;
    static final int MAX_STRINGS = 8;
    static final int GUITAR_STRINGS = 6;
    static final int MAX_FRET = 24;

    private static final Pattern STAFF_LINE = Pattern.compile("^\\s*([A-Ga-g][#b]?)?\\s*\\|(.*)$");

    /**
     * Repeat counts written after a staff line: {@code x3}, {@code 3x}, {@code (x4)}, {@code repeat 3 times}.
     */
    private static final Pattern REPEAT_COUNT =
            Pattern.compile("(?i)x\\s*(\\d{1,2})|(\\d{1,2})\\s*(?:x|times)\\b");

    /**
     * A line without a closing bar line, followed by text after at least two spaces.
     */
    private static final Pattern SPACED_TAIL = Pattern.compile("^(.*?[-|0-9)>~])\\s{2,}(\\S.*)$");

    /**
     * Letters that mean something next to a fret; text made only of these is tab, not a note to the reader.
     */
    private static final String TECHNIQUE_LETTERS = "xXhHpPbBrRsS";

    /**
     * The usual tunings, highest string first.
     */
    private static final Map<Integer, List<String>> GUITAR_TUNINGS = Map.of(
            6, List.of("E4", "B3", "G3", "D3", "A2", "E2"),
            7, List.of("E4", "B3", "G3", "D3", "A2", "E2", "B1"),
            8, List.of("E4", "B3", "G3", "D3", "A2", "E2", "B1", "F#1"));
    private static final Map<Integer, List<String>> BASS_TUNINGS = Map.of(
            4, List.of("G2", "D2", "A1", "E1"),
            5, List.of("G2", "D2", "A1", "E1", "B0"));

    private record StaffLine(String name, String body) {
    }

    /**
     * A note with the columns its fret occupies.
     */
    private record PlacedNote(int start, int end, AsciiNote note) {
    }

    /**
     * @throws IllegalArgumentException if the text contains no staff or no measure
     */
    public AsciiTabScore parse(String text) {
        List<List<StaffLine>> staves = findStaves(text);
        if (staves.isEmpty()) {
            throw new IllegalArgumentException(
                    "No tab found: write one line per string, such as e|---0---|, with at least 4 strings");
        }

        List<AsciiMeasure> measures = new ArrayList<>();
        boolean repeatOpen = false;
        for (List<StaffLine> staff : staves) {
            repeatOpen = parseStaff(staff, measures, repeatOpen);
        }
        if (measures.isEmpty()) {
            throw new IllegalArgumentException("The tab has no measures");
        }
        return new AsciiTabScore(tuning(staves), measures);
    }

    private static List<List<StaffLine>> findStaves(String text) {
        List<List<StaffLine>> staves = new ArrayList<>();
        List<StaffLine> run = new ArrayList<>();
        for (String line : text.replace('\t', ' ').split("\\R")) {
            Matcher matcher = STAFF_LINE.matcher(line);
            if (matcher.matches() && matcher.group(2).chars().filter(c -> c == '-').count() >= 2) {
                run.add(new StaffLine(matcher.group(1), matcher.group(2)));
            } else {
                addStaves(staves, run);
                run = new ArrayList<>();
            }
        }
        addStaves(staves, run);
        return staves;
    }

    /**
     * Splits a run of staff lines into staves: a new staff starts where at least two string names repeat the names
     * the current staff started with. A run of unnamed lines longer than any instrument is cut into guitar staves.
     * Parts shorter than a bass staff are not tab and are left out.
     */
    private static void addStaves(List<List<StaffLine>> staves, List<StaffLine> run) {
        List<List<StaffLine>> parts = new ArrayList<>();
        if (run.stream().allMatch(line -> line.name() == null)) {
            int size = run.size() > MAX_STRINGS ? GUITAR_STRINGS : run.size();
            for (int from = 0; from < run.size(); from += size) {
                parts.add(run.subList(from, Math.min(from + size, run.size())));
            }
        } else {
            int start = 0;
            for (int i = start + MIN_STRINGS; i < run.size(); i++) {
                if (namesStartOver(run, start, i) || i - start == MAX_STRINGS) {
                    parts.add(run.subList(start, i));
                    start = i;
                    i = start + MIN_STRINGS - 1;
                }
            }
            parts.add(run.subList(start, run.size()));
        }
        for (List<StaffLine> part : parts) {
            if (part.size() >= MIN_STRINGS) {
                staves.add(List.copyOf(part));
            }
        }
    }

    private static boolean namesStartOver(List<StaffLine> run, int start, int index) {
        int compared = Math.min(index - start, run.size() - index);
        if (compared < 2) {
            return false;
        }
        for (int k = 0; k < compared; k++) {
            String name = run.get(index + k).name();
            if (name == null || !name.equals(run.get(start + k).name())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Adds the staff's measures. {@code repeatOpen} is a repeat opened at the end of the previous staff; the result
     * is one opened at the end of this staff.
     */
    private static boolean parseStaff(List<StaffLine> staff, List<AsciiMeasure> measures, boolean repeatOpen) {
        List<String> bodies = new ArrayList<>();
        int repeatCount = 0;
        for (StaffLine line : staff) {
            String[] bodyAndNote = splitTrailingNote(line.body());
            bodies.add(bodyAndNote[0]);
            if (bodyAndNote[1] != null) {
                repeatCount = Math.max(repeatCount, repeatCount(bodyAndNote[1]));
            }
        }

        int width = bodies.stream().mapToInt(String::length).max().orElse(0);
        char[] kind = new char[width];
        for (int column = 0; column < width; column++) {
            kind[column] = separatorKind(bodies, column);
        }

        int measuresBefore = measures.size();
        int start = 0;
        int column = 0;
        while (true) {
            while (column < width && kind[column] == 0) {
                column++;
            }
            boolean atEnd = column == width;
            int runEnd = column;
            while (runEnd < width && kind[runEnd] != 0) {
                runEnd++;
            }
            // Dots in the run, or on some strings right next to it, mark a repeat: ":||" closes, "||:" opens
            boolean closes = !atEnd && (kind[column] == ':' || (column > 0 && hasDots(bodies, column - 1)));
            boolean opens = !atEnd && (kind[runEnd - 1] == ':' || hasDots(bodies, runEnd));

            if (column > start) {
                List<AsciiBeat> beats = parseBeats(bodies, start, column);
                if (!atEnd || !beats.isEmpty()) {
                    measures.add(new AsciiMeasure(beats, repeatOpen, closes ? 2 : 0));
                    repeatOpen = false;
                }
            } else if (closes && !measures.isEmpty()) {
                // ":||" at the start of a staff closes the last measure of the previous one
                markRepeatClose(measures, 2);
            }
            if (atEnd) {
                break;
            }
            repeatOpen = repeatOpen || opens;
            start = runEnd;
            column = runEnd;
        }
        if (repeatCount > 0 && measures.size() > measuresBefore) {
            markRepeatClose(measures, repeatCount);
        }
        return repeatOpen;
    }

    /**
     * The line without a note written after it, and that note or {@code null}. The note follows the last bar line
     * or two spaces, has no run of dashes, and is either a repeat count or contains a word.
     */
    private static String[] splitTrailingNote(String body) {
        int lastBar = body.lastIndexOf('|');
        if (lastBar >= 0 && isNote(body.substring(lastBar + 1))) {
            return new String[]{body.substring(0, lastBar + 1), body.substring(lastBar + 1)};
        }
        Matcher tail = SPACED_TAIL.matcher(body);
        if (tail.matches() && isNote(tail.group(2))) {
            return new String[]{tail.group(1), tail.group(2)};
        }
        return new String[]{body, null};
    }

    private static boolean isNote(String text) {
        if (text.contains("--")) {
            return false;
        }
        return text.isBlank() || REPEAT_COUNT.matcher(text).find()
                || text.chars().anyMatch(c -> Character.isLetter(c) && TECHNIQUE_LETTERS.indexOf(c) < 0);
    }

    private static int repeatCount(String note) {
        Matcher count = REPEAT_COUNT.matcher(note);
        if (!count.find()) {
            return 0;
        }
        return Integer.parseInt(count.group(1) != null ? count.group(1) : count.group(2));
    }

    private static void markRepeatClose(List<AsciiMeasure> measures, int count) {
        AsciiMeasure last = measures.removeLast();
        measures.add(new AsciiMeasure(last.beats(), last.repeatOpen(), count));
    }

    /**
     * {@code |} for a bar line, {@code :} for a bar line with repeat dots, 0 for tab: a separator if most of the
     * lines reaching this column have a bar or a dot there.
     */
    private static char separatorKind(List<String> bodies, int column) {
        int reaching = 0;
        int bars = 0;
        int dots = 0;
        for (String body : bodies) {
            if (column < body.length()) {
                reaching++;
                char c = body.charAt(column);
                bars += c == '|' ? 1 : 0;
                dots += isDot(c) ? 1 : 0;
            }
        }
        if ((bars + dots) * 2 <= reaching) {
            return 0;
        }
        return dots > 0 ? ':' : '|';
    }

    private static boolean hasDots(List<String> bodies, int column) {
        return bodies.stream().anyMatch(body -> column < body.length() && isDot(body.charAt(column)));
    }

    private static boolean isDot(char c) {
        return c == ':' || c == '*';
    }

    private static List<AsciiBeat> parseBeats(List<String> bodies, int start, int end) {
        List<PlacedNote> notes = new ArrayList<>();
        for (int line = 0; line < bodies.size(); line++) {
            String body = bodies.get(line);
            notes.addAll(parseNotes(body.substring(Math.min(start, body.length()), Math.min(end, body.length())),
                    line + 1, start));
        }
        notes.sort(Comparator.comparingInt(PlacedNote::start));

        List<AsciiBeat> beats = new ArrayList<>();
        List<AsciiNote> beat = new ArrayList<>();
        int beatEnd = -1;
        for (PlacedNote placed : notes) {
            boolean overlaps = placed.start() < beatEnd;
            boolean stringTaken = beat.stream().anyMatch(note -> note.string() == placed.note().string());
            // A string plays one note per beat, so a second note on it starts the next beat
            if (!beat.isEmpty() && (!overlaps || stringTaken)) {
                beats.add(new AsciiBeat(beat));
                beat = new ArrayList<>();
                beatEnd = -1;
            }
            beat.add(placed.note());
            beatEnd = Math.max(beatEnd, placed.end());
        }
        if (!beat.isEmpty()) {
            beats.add(new AsciiBeat(beat));
        }
        return beats;
    }

    /**
     * The notes of one string in one measure. {@code offset} is the column where {@code segment} starts.
     */
    private static List<PlacedNote> parseNotes(String segment, int string, int offset) {
        List<PlacedNote> notes = new ArrayList<>();
        int i = 0;
        while (i < segment.length()) {
            char c = segment.charAt(i);
            int noteStart = i;
            Integer fret;
            Set<NoteEffect> effects = EnumSet.noneOf(NoteEffect.class);
            if (Character.isDigit(c)) {
                int digits = fretDigits(segment, i);
                fret = Integer.parseInt(segment.substring(i, i + digits));
                i += digits;
            } else if (c == 'x' || c == 'X') {
                fret = null;
                i++;
            } else if ((c == '(' || c == '<') && fretDigits(segment, i + 1) > 0) {
                int digits = fretDigits(segment, i + 1);
                fret = Integer.parseInt(segment.substring(i + 1, i + 1 + digits));
                effects.add(c == '(' ? NoteEffect.GHOST : NoteEffect.NATURAL_HARMONIC);
                i = skip(segment, i + 1 + digits, c == '(' ? ')' : '>');
            } else {
                i++;
                continue;
            }
            int noteEnd = i;

            int bend = 0;
            boolean moreTechniques = true;
            while (moreTechniques && i < segment.length()) {
                switch (Character.toLowerCase(segment.charAt(i))) {
                    case 'h', 'p' -> {
                        // The next note on the string follows
                        effects.add(NoteEffect.HAMMER_PULL);
                        i++;
                        moreTechniques = false;
                    }
                    case '/', '\\', 's' -> {
                        effects.add(NoteEffect.SLIDE);
                        i++;
                        moreTechniques = false;
                    }
                    case '~' -> {
                        effects.add(NoteEffect.VIBRATO);
                        while (i < segment.length() && segment.charAt(i) == '~') {
                            i++;
                        }
                    }
                    case 'b' -> {
                        // The bend target ("7b9", "7b(9)") and a release ("7b9r7") belong to this note
                        i = skip(segment, i + 1, '(');
                        int targetDigits = fretDigits(segment, i);
                        int target = targetDigits > 0 ? Integer.parseInt(segment.substring(i, i + targetDigits)) : -1;
                        i = skip(segment, i + targetDigits, ')');
                        bend = fret != null && target > fret ? (target - fret) * 2 : 4;
                        if (i < segment.length() && Character.toLowerCase(segment.charAt(i)) == 'r') {
                            i = skip(segment, i + 1, '(');
                            i = skip(segment, i + fretDigits(segment, i), ')');
                        }
                    }
                    default -> moreTechniques = false;
                }
            }
            notes.add(new PlacedNote(offset + noteStart, offset + noteEnd, new AsciiNote(string, fret, effects, bend)));
        }
        return notes;
    }

    /**
     * {@code index + 1} if the character at {@code index} is {@code c}, otherwise {@code index}.
     */
    private static int skip(String segment, int index, char c) {
        return index < segment.length() && segment.charAt(index) == c ? index + 1 : index;
    }

    /**
     * How many digits starting at {@code index} form a fret: two if they make a fret on a guitar neck, else one;
     * 0 if there is no digit.
     */
    private static int fretDigits(String segment, int index) {
        if (index >= segment.length() || !Character.isDigit(segment.charAt(index))) {
            return 0;
        }
        if (index + 1 < segment.length() && Character.isDigit(segment.charAt(index + 1))
                && Integer.parseInt(segment.substring(index, index + 2)) <= MAX_FRET) {
            return 2;
        }
        return 1;
    }

    /**
     * The instrument's standard tuning, with the strings renamed after the string names of the largest staff that
     * names all its lines (e.g. a bottom {@code D} for drop D). Octaves stay those of the standard tuning. A guitar
     * tab whose staves lack their low strings still gets all 6 strings.
     */
    private static List<String> tuning(List<List<StaffLine>> staves) {
        int lines = staves.stream().mapToInt(List::size).max().orElseThrow();
        List<StaffLine> named = staves.stream()
                .filter(staff -> staff.stream().allMatch(line -> line.name() != null))
                .max(Comparator.comparingInt(List::size))
                .orElse(null);
        boolean guitar = lines >= GUITAR_STRINGS
                || (named != null && named.getFirst().name().equalsIgnoreCase("e"));
        List<String> standard = guitar
                ? GUITAR_TUNINGS.get(Math.max(lines, GUITAR_STRINGS))
                : BASS_TUNINGS.get(lines);
        if (named == null) {
            return standard;
        }
        List<String> tuning = new ArrayList<>(standard);
        for (int i = 0; i < named.size(); i++) {
            String name = named.get(i).name();
            String octave = standard.get(i).substring(standard.get(i).length() - 1);
            // "eb" is E flat: the letter is upper case, a flat sign stays a lower-case b
            tuning.set(i, name.substring(0, 1).toUpperCase(Locale.ROOT) + name.substring(1) + octave);
        }
        return tuning;
    }
}
