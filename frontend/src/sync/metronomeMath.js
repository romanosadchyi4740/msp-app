/**
 * The arithmetic of the shared metronome (KAN-43, FR-9), kept free of the Web Audio API so it can be tested. Clicks
 * are derived from the same playback state as the position (the server's PlaybackStateDto), so every participant
 * clicks on the same beats: there is no clock of the metronome's own, only the anchor and the synced server clock.
 */

/** Two clicks closer than this are the same click (one planned again after a state change). */
export const SAME_CLICK_MS = 20;

/**
 * A click already late when it is planned is played at once; if one was heard this close to it (the beat the old
 * state clicked just before a tempo change arrived), the two would sound as a flam, so the late one is left out.
 */
export const LATE_SAME_CLICK_MS = 80;

/**
 * Milliseconds between two clicks: one beat of the time signature (a quarter in 4/4, an eighth in 6/8) at the
 * state's tempo, which counts quarter notes per minute.
 */
export function clickIntervalMs(state) {
  return ((4 / state.beatUnit) * 60000) / state.tempoBpm;
}

/**
 * The clicks of a playback state in the server-time window (from, to], in order, as `{ time, accent, countIn }`
 * (`time` in server epoch milliseconds; `accent` on the first beat of a measure).
 *
 * - While playing, the anchor of a state that has just been played lies one measure in the future; that measure is
 *   counted in on the beat grid continued backwards, whether the metronome is on or not, so everyone starts together
 *   and the count-in's pulse runs on into the music (also when playback resumes mid-beat).
 * - From the anchor on, the metronome clicks on every beat of the time signature while it is on. Clicks fall where
 *   the beat grid of the anchor's measure lies, so playback resumed mid-beat clicks on the next beat, and a tempo
 *   change (which re-anchors at the position reached) carries on without a jump.
 * - Paused and stopped playback does not click.
 */
export function clicksBetween(state, from, to) {
  if (!state || state.status !== 'PLAYING' || !(to > from)) return [];
  const clickBeats = 4 / state.beatUnit;
  const msPerQuarter = 60000 / state.tempoBpm;
  const clicks = [];

  // The grid index of the first click at or after the anchor; the epsilon keeps a click exactly on it
  const firstAtAnchor = Math.ceil(state.beat / clickBeats - 1e-9);
  for (let n = state.beatsPerMeasure; n >= 1; n -= 1) {
    const time = state.anchorTime + ((firstAtAnchor - n) * clickBeats - state.beat) * msPerQuarter;
    if (time > from && time <= to) {
      clicks.push({ time, accent: n === state.beatsPerMeasure, countIn: true });
    }
  }

  if (state.metronomeOn) {
    const start = Math.max(from, state.anchorTime);
    const quarterBeatsAtStart = state.beat + (start - state.anchorTime) / msPerQuarter;
    // The grid index of the first click at or after the start; the epsilon keeps a click exactly on it
    for (let k = Math.ceil(quarterBeatsAtStart / clickBeats - 1e-9); ; k += 1) {
      const time = state.anchorTime + (k * clickBeats - state.beat) * msPerQuarter;
      if (time > to) break;
      if (time > from && time >= state.anchorTime) {
        clicks.push({ time, accent: mod(k, state.beatsPerMeasure) === 0, countIn: false });
      }
    }
  }
  return clicks;
}

/**
 * The clicks not yet played: those not within SAME_CLICK_MS of one already scheduled. After a state change the
 * clicks are planned again from slightly before now, and one that sounded under the old state must not sound twice.
 * A click due before `now` (it will sound late) is also left out within LATE_SAME_CLICK_MS of one scheduled.
 */
export function withoutPlayed(clicks, playedTimes, now = -Infinity) {
  return clicks.filter((click) => {
    const minGap = click.time < now ? LATE_SAME_CLICK_MS : SAME_CLICK_MS;
    return playedTimes.every((played) => Math.abs(played - click.time) >= minGap);
  });
}

/**
 * The AudioContext time at which a click at `serverTime` must be started to be heard then. `outputTimestamp` is
 * AudioContext.getOutputTimestamp(): the context time being heard right at `performanceTime`, so the output latency
 * is accounted for. `serverMinusPerformance` is serverNow() - performance.now(), mapping performance time to server
 * time.
 */
export function audioTimeFor(serverTime, outputTimestamp, serverMinusPerformance) {
  const heardServerTime = outputTimestamp.performanceTime + serverMinusPerformance;
  return outputTimestamp.contextTime + (serverTime - heardServerTime) / 1000;
}

function mod(value, modulus) {
  return ((value % modulus) + modulus) % modulus;
}
