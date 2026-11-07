package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import lombok.experimental.UtilityClass;

/**
 * The anchor-time model of a lesson's playback (lab 7: {@code PlaybackState.apply}, {@code positionAt}). The state
 * holds where playback was at {@code anchorTime}; while playing, the position moves on by
 * {@code (t - anchorTime) * tempoBpm / 60000} quarter beats, wrapped into measures by the time signature. Commands
 * set a new anchor instead of streaming positions. A command that changes nothing leaves every field as it was, so
 * the stored version does not grow.
 */
@UtilityClass
public class PlaybackStates {

    final int FIRST_MEASURE = 1;
    final int MIN_TEMPO_BPM = 20;
    final int MAX_TEMPO_BPM = 400;
    /**
     * How long after PLAY the count-in starts. The new state reaches the participants a network delay after the
     * server applied it; without the lead, the first click of the count-in would be due before anyone heard of it
     * (KAN-43).
     */
    final long COUNT_IN_LEAD_MS = 250;

    /**
     * Where playback is at {@code serverTime}. Before the anchor, during the count-in, it is still at the stored
     * position; paused and stopped playback does not move.
     */
    public Position positionAt(PlaybackState state, long serverTime) {
        if (state.getStatus() != PlaybackStatus.PLAYING || serverTime <= state.getAnchorTime()) {
            return new Position(state.getMeasure(), state.getBeat());
        }
        double measureLength = timeSignatureOf(state).quarterBeatsPerMeasure();
        double beats = state.getBeat() + (serverTime - state.getAnchorTime()) * state.getTempoBpm() / 60000.0;
        long measures = (long) Math.floor(beats / measureLength);
        double beat = Math.max(0, beats - measures * measureLength);
        // Rounding can leave a full measure just below a boundary
        if (beat >= measureLength) {
            beat -= measureLength;
            measures++;
        }
        return new Position(Math.toIntExact(state.getMeasure() + measures), beat);
    }

    /**
     * Applies a command the server received at {@code serverNow}.
     *
     * @throws IllegalArgumentException if the command has no type, an invalid argument, or is
     *                                  {@code REQUEST_SYNC}, which asks for a snapshot and changes nothing
     */
    public void apply(PlaybackState state, SyncCommand command, long serverNow) {
        if (command == null || command.type() == null) {
            throw new IllegalArgumentException("A command type is required");
        }
        switch (command.type()) {
            case PLAY -> play(state, serverNow);
            case PAUSE -> pause(state, serverNow);
            case SEEK -> seek(state, parseMeasure(command.argument()), serverNow);
            case SET_TEMPO -> setTempo(state, parseTempo(command.argument()), serverNow);
            case METRONOME -> state.setMetronomeOn(parseSwitch(command.argument()));
            case LOAD_TAB -> loadTab(state, state.getTempoBpm(), timeSignatureOf(state), serverNow);
            case REQUEST_SYNC -> throw new IllegalArgumentException("REQUEST_SYNC asks for a snapshot, not a change");
        }
    }

    /**
     * Back to the first measure, stopped, at the tab's tempo and time signature; the metronome stays as it is. A tab
     * tempo outside what {@code SET_TEMPO} accepts is brought to the nearest bound, so the count-in stays sensible.
     */
    public void loadTab(PlaybackState state, int tempoBpm, TimeSignature timeSignature, long serverNow) {
        state.setMeasure(FIRST_MEASURE);
        state.setBeat(0);
        state.setTempoBpm(Math.clamp(tempoBpm, MIN_TEMPO_BPM, MAX_TEMPO_BPM));
        state.setBeatsPerMeasure(timeSignature.beatsPerMeasure());
        state.setBeatUnit(timeSignature.beatUnit());
        state.setStatus(PlaybackStatus.STOPPED);
        state.setAnchorTime(serverNow);
    }

    /**
     * Stops where playback is at {@code serverNow}.
     */
    public void stopAt(PlaybackState state, long serverNow) {
        if (state.getStatus() == PlaybackStatus.PLAYING) {
            freeze(state, serverNow);
        }
        state.setStatus(PlaybackStatus.STOPPED);
    }

    /**
     * One measure at the current tempo, counted in before playback starts.
     */
    public long countInMillis(PlaybackState state) {
        return Math.round(timeSignatureOf(state).quarterBeatsPerMeasure() * 60000.0 / state.getTempoBpm());
    }

    public TimeSignature timeSignatureOf(PlaybackState state) {
        return new TimeSignature(state.getBeatsPerMeasure(), state.getBeatUnit());
    }

    /**
     * Plays on from the stored position after a one-measure count-in that starts {@code COUNT_IN_LEAD_MS} from now,
     * so the anchor lies in the future.
     */
    private void play(PlaybackState state, long serverNow) {
        if (state.getStatus() == PlaybackStatus.PLAYING) {
            return;
        }
        state.setStatus(PlaybackStatus.PLAYING);
        state.setAnchorTime(serverNow + COUNT_IN_LEAD_MS + countInMillis(state));
    }

    private void pause(PlaybackState state, long serverNow) {
        if (state.getStatus() != PlaybackStatus.PLAYING) {
            return;
        }
        freeze(state, serverNow);
        state.setStatus(PlaybackStatus.PAUSED);
    }

    /**
     * Jumps to the start of the measure; playing goes on from there at once, without a new count-in, and a count-in
     * that is still running keeps its end.
     */
    private void seek(PlaybackState state, int measure, long serverNow) {
        state.setMeasure(measure);
        state.setBeat(0);
        if (state.getStatus() == PlaybackStatus.PLAYING) {
            state.setAnchorTime(Math.max(serverNow, state.getAnchorTime()));
        }
    }

    /**
     * While playing, the position reached so far becomes the new anchor, so it does not jump; a count-in that is
     * still running keeps its end.
     */
    private void setTempo(PlaybackState state, int tempoBpm, long serverNow) {
        if (state.getStatus() == PlaybackStatus.PLAYING) {
            long anchorTime = Math.max(serverNow, state.getAnchorTime());
            Position position = positionAt(state, serverNow);
            state.setMeasure(position.measure());
            state.setBeat(position.beat());
            state.setAnchorTime(anchorTime);
        }
        state.setTempoBpm(tempoBpm);
    }

    private void freeze(PlaybackState state, long serverNow) {
        Position position = positionAt(state, serverNow);
        state.setMeasure(position.measure());
        state.setBeat(position.beat());
        state.setAnchorTime(serverNow);
    }

    private int parseMeasure(String argument) {
        int measure = parseInt(argument, "SEEK needs a measure number");
        if (measure < FIRST_MEASURE) {
            throw new IllegalArgumentException("Measures are counted from 1");
        }
        return measure;
    }

    private int parseTempo(String argument) {
        int tempoBpm = parseInt(argument, "SET_TEMPO needs a tempo in beats per minute");
        if (tempoBpm < MIN_TEMPO_BPM || tempoBpm > MAX_TEMPO_BPM) {
            throw new IllegalArgumentException(
                    "Tempo must be between " + MIN_TEMPO_BPM + " and " + MAX_TEMPO_BPM + " beats per minute");
        }
        return tempoBpm;
    }

    private boolean parseSwitch(String argument) {
        if ("true".equalsIgnoreCase(argument)) {
            return true;
        }
        if ("false".equalsIgnoreCase(argument)) {
            return false;
        }
        throw new IllegalArgumentException("METRONOME needs true or false");
    }

    private int parseInt(String argument, String message) {
        if (argument == null) {
            throw new IllegalArgumentException(message);
        }
        try {
            return Integer.parseInt(argument.strip());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(message);
        }
    }
}
