/**
 * The arithmetic of the shared metronome (KAN-43, FR-9), kept free of the Web Audio API so it can be tested. Clicks
 * are derived from the same playback state as the position (the server's PlaybackStateDto), so every participant
 * clicks on the same beats: there is no clock of the metronome's own, only the anchor and the synced server clock.
 */

import { measureTable, quarterBeatsPerMeasure } from './playbackMath.js';

/** Two clicks closer than this are the same click (one planned again after a state change). */
export const SAME_CLICK_MS = 20;

/**
 * A click already late when it is planned is played at once; if one was heard this close to it (the beat the old
 * state clicked just before a tempo change arrived), the two would sound as a flam, so the late one is left out.
 */
export const LATE_SAME_CLICK_MS = 80;

/**
 * Milliseconds between two clicks in a time signature: one beat of it (a quarter in 4/4, an eighth in 6/8) at the
 * state's tempo, which counts quarter notes per minute.
 */
export function clickIntervalMs(state, signature = state) {
  return ((4 / signature.beatUnit) * 60000) / state.tempoBpm;
}

/**
 * The clicks of a playback state in the server-time window (from, to], in order, as `{ time, accent, countIn }`
 * (`time` in server epoch milliseconds; `accent` on the first beat of a measure). `measures` is the tab's measure
 * table (playbackMath.measureTable), so every measure clicks in its own time signature; without it every measure has
 * the state's.
 *
 * - While playing, the anchor of a state that has just been played lies one measure (of the tab's time signature) in
 *   the future; that time is counted in on the beat grid of the measure playback starts in, continued backwards,
 *   whether the metronome is on or not, so everyone starts together and the count-in's pulse runs on into the music
 *   (also when playback resumes mid-beat).
 * - From the anchor on, the metronome clicks on every beat of each measure's time signature while it is on, so a
 *   tempo change (which re-anchors at the position reached) carries on without a jump.
 * - Paused and stopped playback does not click.
 */
export function clicksBetween(state, from, to, measures = null) {
  if (!state || state.status !== 'PLAYING' || !(to > from)) return [];
  const table = measures ?? measureTable(state);
  const msPerQuarter = 60000 / state.tempoBpm;
  const anchorBeats = table.startOf(state.measure) + state.beat;
  const timeOf = (quarterBeats) => state.anchorTime + (quarterBeats - anchorBeats) * msPerQuarter;
  const clicks = [];

  const resumed = table.measureAt(anchorBeats);
  const signature = table.signatureOf(resumed.measure);
  const clickBeats = 4 / signature.beatUnit;
  // The grid index of the first click at or after the anchor; the epsilon keeps a click exactly on it
  const firstAtAnchor = Math.ceil(resumed.beat / clickBeats - 1e-9);
  const countInStart = state.anchorTime - quarterBeatsPerMeasure(state) * msPerQuarter;
  const countIn = [];
  for (let n = signature.beatsPerMeasure; n >= 1; n -= 1) {
    const time = state.anchorTime + ((firstAtAnchor - n) * clickBeats - resumed.beat) * msPerQuarter;
    if (time >= countInStart - 1e-6) countIn.push(time);
  }
  countIn.forEach((time, index) => {
    if (time > from && time <= to) clicks.push({ time, accent: index === 0, countIn: true });
  });

  if (state.metronomeOn) {
    const startBeats = anchorBeats + Math.max(0, (from - state.anchorTime) / msPerQuarter);
    for (let { measure } = table.measureAt(startBeats); ; measure += 1) {
      const { beatsPerMeasure, beatUnit } = table.signatureOf(measure);
      const measureStart = table.startOf(measure);
      for (let k = 0; k < beatsPerMeasure; k += 1) {
        const quarterBeats = measureStart + (k * 4) / beatUnit;
        const time = timeOf(quarterBeats);
        if (time > to) return clicks;
        if (time > from && quarterBeats >= anchorBeats - 1e-9) {
          clicks.push({ time, accent: k === 0, countIn: false });
        }
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
