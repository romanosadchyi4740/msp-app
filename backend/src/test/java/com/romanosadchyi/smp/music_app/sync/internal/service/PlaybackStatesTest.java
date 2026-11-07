package com.romanosadchyi.smp.music_app.sync.internal.service;

import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommand;
import com.romanosadchyi.smp.music_app.sync.internal.dto.SyncCommandType;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackState;
import com.romanosadchyi.smp.music_app.sync.internal.entity.PlaybackStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlaybackStatesTest {

    static final long T0 = 1_800_000_000_000L;

    // positionAt

    @Test
    void positionAt_Playing_MovesOnByTheElapsedBeats() {
        // 120 BPM is 2 quarter beats per second
        PlaybackState state = playing(1, 0, 120, 4, 4, T0);

        assertThat(PlaybackStates.positionAt(state, T0 + 1500)).isEqualTo(new Position(1, 3.0));
    }

    @Test
    void positionAt_CrossesTheMeasureBoundary() {
        PlaybackState state = playing(1, 3.5, 120, 4, 4, T0);

        assertThat(PlaybackStates.positionAt(state, T0 + 500)).isEqualTo(new Position(2, 0.5));
    }

    @Test
    void positionAt_ExactlyOnTheBoundary_IsTheNextMeasuresFirstBeat() {
        PlaybackState state = playing(1, 0, 120, 4, 4, T0);

        assertThat(PlaybackStates.positionAt(state, T0 + 2000)).isEqualTo(new Position(2, 0.0));
    }

    @Test
    void positionAt_SeveralMeasures() {
        PlaybackState state = playing(7, 0, 120, 4, 4, T0);

        assertThat(PlaybackStates.positionAt(state, T0 + 10_000)).isEqualTo(new Position(12, 0.0));
    }

    @Test
    void positionAt_ThreeFour_WrapsAfterThreeBeats() {
        PlaybackState state = playing(1, 0, 120, 3, 4, T0);

        assertThat(PlaybackStates.positionAt(state, T0 + 2000)).isEqualTo(new Position(2, 1.0));
        assertThat(PlaybackStates.positionAt(state, T0 + 3000)).isEqualTo(new Position(3, 0.0));
    }

    @Test
    void positionAt_SixEight_WrapsAfterThreeQuarterBeats() {
        // A 6/8 measure holds six eighths, three quarter beats; at 90 BPM 4 s are 6 quarter beats, two measures
        PlaybackState state = playing(1, 0, 90, 6, 8, T0);

        assertThat(PlaybackStates.positionAt(state, T0 + 4000)).isEqualTo(new Position(3, 0.0));
        assertThat(PlaybackStates.positionAt(state, T0 + 1000)).isEqualTo(new Position(1, 1.5));
    }

    @Test
    void positionAt_BeforeTheAnchor_StaysAtTheStoredPosition() {
        PlaybackState state = playing(5, 2, 120, 4, 4, T0 + 2000);

        assertThat(PlaybackStates.positionAt(state, T0)).isEqualTo(new Position(5, 2.0));
    }

    @Test
    void positionAt_PausedOrStopped_DoesNotMove() {
        PlaybackState paused = state(5, 2, 120, 4, 4, PlaybackStatus.PAUSED, T0);
        PlaybackState stopped = state(5, 2, 120, 4, 4, PlaybackStatus.STOPPED, T0);

        assertThat(PlaybackStates.positionAt(paused, T0 + 60_000)).isEqualTo(new Position(5, 2.0));
        assertThat(PlaybackStates.positionAt(stopped, T0 + 60_000)).isEqualTo(new Position(5, 2.0));
    }

    // PLAY

    @Test
    void play_StartsAfterTheLeadAndAOneMeasureCountIn() {
        PlaybackState state = state(3, 1, 120, 4, 4, PlaybackStatus.PAUSED, 0);

        PlaybackStates.apply(state, command(SyncCommandType.PLAY, null), T0);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.PLAYING);
        assertThat(state.getAnchorTime()).isEqualTo(T0 + 250 + 2000);
        assertThat(state.getMeasure()).isEqualTo(3);
        assertThat(state.getBeat()).isEqualTo(1.0);
    }

    @Test
    void play_CountInLastsOneMeasureOfTheTimeSignature() {
        PlaybackState threeFour = state(1, 0, 120, 3, 4, PlaybackStatus.STOPPED, 0);
        PlaybackState sixEight = state(1, 0, 120, 6, 8, PlaybackStatus.STOPPED, 0);

        PlaybackStates.apply(threeFour, command(SyncCommandType.PLAY, null), T0);
        PlaybackStates.apply(sixEight, command(SyncCommandType.PLAY, null), T0);

        assertThat(threeFour.getAnchorTime()).isEqualTo(T0 + 250 + 1500);
        assertThat(sixEight.getAnchorTime()).isEqualTo(T0 + 250 + 1500);
    }

    @Test
    void play_AlreadyPlaying_ChangesNothing() {
        PlaybackState state = playing(3, 1, 120, 4, 4, T0);

        PlaybackStates.apply(state, command(SyncCommandType.PLAY, null), T0 + 5000);

        assertThat(state.getAnchorTime()).isEqualTo(T0);
        assertThat(state.getMeasure()).isEqualTo(3);
        assertThat(state.getBeat()).isEqualTo(1.0);
    }

    // PAUSE

    @Test
    void pause_FreezesThePositionReachedNow() {
        PlaybackState state = playing(1, 3.5, 120, 4, 4, T0);

        PlaybackStates.apply(state, command(SyncCommandType.PAUSE, null), T0 + 500);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.PAUSED);
        assertThat(state.getMeasure()).isEqualTo(2);
        assertThat(state.getBeat()).isEqualTo(0.5);
        assertThat(state.getAnchorTime()).isEqualTo(T0 + 500);
    }

    @Test
    void pause_DuringTheCountIn_KeepsTheStartPosition() {
        PlaybackState state = playing(4, 0, 120, 4, 4, T0 + 2000);

        PlaybackStates.apply(state, command(SyncCommandType.PAUSE, null), T0 + 1000);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.PAUSED);
        assertThat(state.getMeasure()).isEqualTo(4);
        assertThat(state.getBeat()).isZero();
        assertThat(state.getAnchorTime()).isEqualTo(T0 + 1000);
    }

    @Test
    void pause_NotPlaying_ChangesNothing() {
        PlaybackState state = state(4, 2, 120, 4, 4, PlaybackStatus.STOPPED, T0);

        PlaybackStates.apply(state, command(SyncCommandType.PAUSE, null), T0 + 1000);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getAnchorTime()).isEqualTo(T0);
    }

    // SEEK

    @Test
    void seek_Paused_JumpsToTheMeasureStartAndKeepsTheAnchor() {
        PlaybackState state = state(4, 2, 120, 4, 4, PlaybackStatus.PAUSED, T0);

        PlaybackStates.apply(state, command(SyncCommandType.SEEK, "12"), T0 + 1000);

        assertThat(state.getMeasure()).isEqualTo(12);
        assertThat(state.getBeat()).isZero();
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.PAUSED);
        assertThat(state.getAnchorTime()).isEqualTo(T0);
    }

    @Test
    void seek_Playing_PlaysOnFromTheMeasureAtOnce() {
        PlaybackState state = playing(4, 2, 120, 4, 4, T0);

        PlaybackStates.apply(state, command(SyncCommandType.SEEK, " 8 "), T0 + 1000);

        assertThat(state.getAnchorTime()).isEqualTo(T0 + 1000);
        assertThat(PlaybackStates.positionAt(state, T0 + 1500)).isEqualTo(new Position(8, 1.0));
    }

    @Test
    void seek_DuringTheCountIn_KeepsItsEnd() {
        PlaybackState state = playing(4, 0, 120, 4, 4, T0 + 2000);

        PlaybackStates.apply(state, command(SyncCommandType.SEEK, "10"), T0 + 500);

        assertThat(state.getAnchorTime()).isEqualTo(T0 + 2000);
        assertThat(PlaybackStates.positionAt(state, T0 + 1000)).isEqualTo(new Position(10, 0.0));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"0", "-3", "abc", "1.5", ""})
    void seek_InvalidMeasure_IsRejected(String argument) {
        PlaybackState state = playing(4, 2, 120, 4, 4, T0);

        assertThatThrownBy(() -> PlaybackStates.apply(state, command(SyncCommandType.SEEK, argument), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(state.getMeasure()).isEqualTo(4);
    }

    // SET_TEMPO

    @Test
    void setTempo_Playing_DoesNotMoveThePosition() {
        PlaybackState state = playing(1, 0, 120, 4, 4, T0);
        long now = T0 + 1000;
        Position before = PlaybackStates.positionAt(state, now);

        PlaybackStates.apply(state, command(SyncCommandType.SET_TEMPO, "60"), now);

        assertThat(before).isEqualTo(new Position(1, 2.0));
        assertThat(PlaybackStates.positionAt(state, now)).isEqualTo(before);
        assertThat(state.getTempoBpm()).isEqualTo(60);
        assertThat(state.getAnchorTime()).isEqualTo(now);
        // From now on one quarter beat per second
        assertThat(PlaybackStates.positionAt(state, now + 1000)).isEqualTo(new Position(1, 3.0));
        assertThat(PlaybackStates.positionAt(state, now + 2000)).isEqualTo(new Position(2, 0.0));
    }

    @Test
    void setTempo_DuringTheCountIn_KeepsItsEnd() {
        PlaybackState state = playing(3, 0, 120, 4, 4, T0 + 2000);

        PlaybackStates.apply(state, command(SyncCommandType.SET_TEMPO, "90"), T0 + 500);

        assertThat(state.getAnchorTime()).isEqualTo(T0 + 2000);
        assertThat(state.getMeasure()).isEqualTo(3);
        assertThat(state.getBeat()).isZero();
        assertThat(state.getTempoBpm()).isEqualTo(90);
    }

    @Test
    void setTempo_Paused_ChangesOnlyTheTempo() {
        PlaybackState state = state(3, 1, 120, 4, 4, PlaybackStatus.PAUSED, T0);

        PlaybackStates.apply(state, command(SyncCommandType.SET_TEMPO, "200"), T0 + 5000);

        assertThat(state.getTempoBpm()).isEqualTo(200);
        assertThat(state.getAnchorTime()).isEqualTo(T0);
        assertThat(state.getMeasure()).isEqualTo(3);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"19", "401", "fast", "0"})
    void setTempo_OutOfRangeOrNotANumber_IsRejected(String argument) {
        PlaybackState state = playing(1, 0, 120, 4, 4, T0);

        assertThatThrownBy(() -> PlaybackStates.apply(state, command(SyncCommandType.SET_TEMPO, argument), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(state.getTempoBpm()).isEqualTo(120);
    }

    @Test
    void setTempo_AcceptsTheBounds() {
        PlaybackState state = state(1, 0, 120, 4, 4, PlaybackStatus.STOPPED, T0);

        PlaybackStates.apply(state, command(SyncCommandType.SET_TEMPO, "20"), T0);
        assertThat(state.getTempoBpm()).isEqualTo(20);
        PlaybackStates.apply(state, command(SyncCommandType.SET_TEMPO, "400"), T0);
        assertThat(state.getTempoBpm()).isEqualTo(400);
    }

    // METRONOME

    @Test
    void metronome_SwitchesOnAndOff() {
        PlaybackState state = state(1, 0, 120, 4, 4, PlaybackStatus.STOPPED, T0);

        PlaybackStates.apply(state, command(SyncCommandType.METRONOME, "TRUE"), T0);
        assertThat(state.isMetronomeOn()).isTrue();
        PlaybackStates.apply(state, command(SyncCommandType.METRONOME, "false"), T0);
        assertThat(state.isMetronomeOn()).isFalse();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"on", "1", ""})
    void metronome_OtherArguments_AreRejected(String argument) {
        PlaybackState state = state(1, 0, 120, 4, 4, PlaybackStatus.STOPPED, T0);

        assertThatThrownBy(() -> PlaybackStates.apply(state, command(SyncCommandType.METRONOME, argument), T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // LOAD_TAB, loadTab, stopAt

    @Test
    void loadTabCommand_RewindsAndStopsWithTheSameTempoAndSignature() {
        PlaybackState state = playing(9, 2, 100, 6, 8, T0);
        state.setMetronomeOn(true);

        PlaybackStates.apply(state, command(SyncCommandType.LOAD_TAB, null), T0 + 1000);

        assertThat(state.getMeasure()).isEqualTo(1);
        assertThat(state.getBeat()).isZero();
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getAnchorTime()).isEqualTo(T0 + 1000);
        assertThat(state.getTempoBpm()).isEqualTo(100);
        assertThat(PlaybackStates.timeSignatureOf(state)).isEqualTo(new TimeSignature(6, 8));
        assertThat(state.isMetronomeOn()).isTrue();
    }

    @Test
    void loadTab_SetsTheNewTabsTempoAndSignature() {
        PlaybackState state = playing(9, 2, 100, 4, 4, T0);

        PlaybackStates.loadTab(state, 72, new TimeSignature(3, 4), T0 + 1000);

        assertThat(state.getMeasure()).isEqualTo(1);
        assertThat(state.getTempoBpm()).isEqualTo(72);
        assertThat(state.getBeatsPerMeasure()).isEqualTo(3);
        assertThat(state.getBeatUnit()).isEqualTo(4);
        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
    }

    @Test
    void loadTab_TempoOutsideTheRange_IsBroughtToTheNearestBound() {
        PlaybackState slow = playing(9, 2, 100, 4, 4, T0);
        PlaybackState fast = playing(9, 2, 100, 4, 4, T0);

        PlaybackStates.loadTab(slow, 1, TimeSignature.COMMON, T0);
        PlaybackStates.loadTab(fast, 5000, TimeSignature.COMMON, T0);

        assertThat(slow.getTempoBpm()).isEqualTo(20);
        assertThat(fast.getTempoBpm()).isEqualTo(400);
        assertThat(PlaybackStates.countInMillis(slow)).isEqualTo(12_000);
    }

    @Test
    void positionAt_NeverReturnsABeatOfAFullMeasure() {
        // 7/8 holds 3.5 quarter beats, a length that rounding can leave just below the boundary
        for (int tempoBpm = 20; tempoBpm <= 400; tempoBpm += 7) {
            PlaybackState state = playing(1, 0.1, tempoBpm, 7, 8, T0);
            for (long elapsed = 0; elapsed <= 20_000; elapsed += 37) {
                assertThat(PlaybackStates.positionAt(state, T0 + elapsed).beat()).isLessThan(3.5);
            }
        }
    }

    @Test
    void stopAt_Playing_StopsWhereItIsNow() {
        PlaybackState state = playing(1, 0, 120, 3, 4, T0);

        PlaybackStates.stopAt(state, T0 + 2000);

        assertThat(state.getStatus()).isEqualTo(PlaybackStatus.STOPPED);
        assertThat(state.getMeasure()).isEqualTo(2);
        assertThat(state.getBeat()).isEqualTo(1.0);
        assertThat(state.getAnchorTime()).isEqualTo(T0 + 2000);
    }

    // Rejected commands

    @Test
    void requestSync_IsNotAChange() {
        PlaybackState state = playing(1, 0, 120, 4, 4, T0);

        assertThatThrownBy(() -> PlaybackStates.apply(state, command(SyncCommandType.REQUEST_SYNC, null), T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void commandWithoutType_IsRejected() {
        PlaybackState state = playing(1, 0, 120, 4, 4, T0);

        assertThatThrownBy(() -> PlaybackStates.apply(state, command(null, null), T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlaybackStates.apply(state, null, T0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SyncCommand command(SyncCommandType type, String argument) {
        return new SyncCommand(type, argument, UUID.randomUUID(), null);
    }

    private static PlaybackState playing(int measure, double beat, int tempoBpm, int beatsPerMeasure, int beatUnit,
                                         long anchorTime) {
        return state(measure, beat, tempoBpm, beatsPerMeasure, beatUnit, PlaybackStatus.PLAYING, anchorTime);
    }

    private static PlaybackState state(int measure, double beat, int tempoBpm, int beatsPerMeasure, int beatUnit,
                                       PlaybackStatus status, long anchorTime) {
        return PlaybackState.builder()
                .sessionId(UUID.randomUUID())
                .measure(measure)
                .beat(beat)
                .tempoBpm(tempoBpm)
                .beatsPerMeasure(beatsPerMeasure)
                .beatUnit(beatUnit)
                .status(status)
                .anchorTime(anchorTime)
                .build();
    }
}
