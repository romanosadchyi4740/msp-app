/**
 * The client side of the anchor-time model (KAN-42), kept free of the socket, the store and the browser so it can be
 * tested. A playback state is the server's PlaybackStateDto: where playback was at `anchorTime` (server epoch
 * milliseconds), as a 1-based `measure` and `beat` in quarter beats into it, and how it moves on from there.
 */

/** A shown position further than this from the expected one is drift (NFR-1 allows 150 ms between participants). */
export const MAX_DRIFT_MS = 150;

/**
 * How many quarter beats one measure holds: 4 in 4/4, 3 in 3/4 and in 6/8.
 */
export function quarterBeatsPerMeasure(state) {
  return (state.beatsPerMeasure * 4) / state.beatUnit;
}

/**
 * Where playback is at `serverTime`, as `{ measure, beat }` in the server's terms (1-based measure, quarter beats).
 * The same computation as the server's PlaybackStates.positionAt: before the anchor, during the count-in, playback
 * is still at the stored position, and paused or stopped playback does not move.
 */
export function positionAt(state, serverTime) {
  if (state.status !== 'PLAYING' || serverTime <= state.anchorTime) {
    return { measure: state.measure, beat: state.beat };
  }
  const measureLength = quarterBeatsPerMeasure(state);
  const beats = state.beat + ((serverTime - state.anchorTime) * state.tempoBpm) / 60000;
  let measures = Math.floor(beats / measureLength);
  let beat = Math.max(0, beats - measures * measureLength);
  // Rounding can leave a full measure just below a boundary
  if (beat >= measureLength) {
    beat -= measureLength;
    measures += 1;
  }
  return { measure: state.measure + measures, beat };
}

/**
 * A server position in TabCanvas terms: a 0-based measure, and the beat in beats of the time signature (in 6/8 a
 * measure is 3 quarter beats but 6 eighth-note beats).
 */
export function toCanvasPosition(position, state) {
  return { measure: position.measure - 1, beat: (position.beat * state.beatUnit) / 4 };
}

/**
 * Whether a received state is newer than the applied one. Versions count per lesson, so a state of another lesson
 * (a new one has started) is always taken; within a lesson, one whose version was already applied is dropped, as
 * messages can arrive after a newer snapshot.
 */
export function isNewerState(applied, incoming) {
  if (!incoming) return false;
  if (!applied || applied.sessionId !== incoming.sessionId) return true;
  return incoming.version > applied.version;
}

/**
 * Whether the position computed now differs from the expected one by more than MAX_DRIFT_MS, so a snapshot is worth
 * asking for. `previous` and `current` are frames as `{ serverTime, localTime }`, where `localTime` is a monotonic
 * clock (performance.now()). Between two frames the server clock should move on exactly as far as the monotonic one,
 * however long the frame took; when it does not, the wall clock or the clock offset jumped (a changed system time, a
 * laptop waking up) and positions computed on either side of the jump disagree. A slow frame alone is no drift: every
 * frame computes the position afresh from the state.
 */
export function needsResync(previous, current) {
  if (!previous) return false;
  const localElapsed = current.localTime - previous.localTime;
  const serverElapsed = current.serverTime - previous.serverTime;
  return Math.abs(serverElapsed - localElapsed) > MAX_DRIFT_MS;
}
