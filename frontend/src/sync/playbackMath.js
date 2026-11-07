/**
 * The client side of the anchor-time model (KAN-42), kept free of the socket, the store and the browser so it can be
 * tested. A playback state is the server's PlaybackStateDto: where playback was at `anchorTime` (server epoch
 * milliseconds), as a 1-based `measure` and the quarter beats since its start (`beat`), and how it moves on from
 * there. The server does not wrap positions into measures (KAN-95): it cannot know how long each measure of a Guitar
 * Pro tab is. The client does, from the score it renders, and wraps with a measure table.
 */

/** A shown position further than this from the expected one is drift (NFR-1 allows 150 ms between participants). */
export const MAX_DRIFT_MS = 150;

/** Below this, two positions in quarter beats are the same (rounding of the elapsed time). */
const EPSILON = 1e-9;

/**
 * How many quarter beats one measure of a time signature (`{ beatsPerMeasure, beatUnit }`, e.g. a playback state)
 * holds: 4 in 4/4, 3 in 3/4 and in 6/8.
 */
export function quarterBeatsPerMeasure(signature) {
  return (signature.beatsPerMeasure * 4) / signature.beatUnit;
}

/**
 * The measures of the lesson's tab: where each starts and how it is counted, in quarter beats from the start of the
 * tab. `signatures` lists every measure's time signature in order, as `{ beatsPerMeasure, beatUnit }`, from the
 * score the client renders; measures past the list keep its last signature. Without a list (the tab is not loaded,
 * or not visible to this participant) every measure has the state's time signature, the tab's.
 *
 * - `signatureOf(measure)`: the time signature of a 1-based measure.
 * - `startOf(measure)`: the quarter beats before it.
 * - `measureAt(quarterBeats)`: the position `{ measure, beat }` that many quarter beats from the start, `beat` in
 *   quarter beats into the measure, always less than its length.
 */
export function measureTable(state, signatures = null) {
  const list = signatures?.length ? signatures : [];
  const last = list.length ? list[list.length - 1] : { beatsPerMeasure: state.beatsPerMeasure, beatUnit: state.beatUnit };
  const lastLength = quarterBeatsPerMeasure(last);
  // starts[i]: the quarter beats before measure i + 1; starts[list.length] is where the listed measures end
  const starts = [0];
  list.forEach((signature) => starts.push(starts[starts.length - 1] + quarterBeatsPerMeasure(signature)));
  const end = starts[list.length];

  // Measures count from 1; the server never sends a smaller one
  const signatureOf = (measure) => list[Math.max(1, measure) - 1] ?? last;
  const startOf = (measure) => {
    const index = Math.max(1, measure) - 1;
    return index <= list.length ? starts[index] : end + (index - list.length) * lastLength;
  };

  const measureAt = (quarterBeats) => {
    const at = Math.max(0, quarterBeats);
    let index;
    if (at >= end - EPSILON) {
      index = list.length + Math.max(0, Math.floor((at - end) / lastLength + EPSILON));
    } else {
      // The last listed measure starting at or before `at`
      let low = 0;
      let high = list.length - 1;
      while (low < high) {
        const middle = Math.ceil((low + high) / 2);
        if (starts[middle] <= at + EPSILON) low = middle;
        else high = middle - 1;
      }
      index = low;
    }
    const measure = index + 1;
    return { measure, beat: Math.max(0, at - startOf(measure)) };
  };

  return { signatureOf, startOf, measureAt };
}

/**
 * Where playback is at `serverTime`, in quarter beats from the start of the tab. Before the anchor, during the
 * count-in, playback is still at the stored position, and paused or stopped playback does not move; the same as the
 * server's PlaybackStates.positionAt.
 */
export function quarterBeatsAt(state, serverTime, table = measureTable(state)) {
  const stored = table.startOf(state.measure) + state.beat;
  if (state.status !== 'PLAYING' || serverTime <= state.anchorTime) return stored;
  return stored + ((serverTime - state.anchorTime) * state.tempoBpm) / 60000;
}

/**
 * Where playback is at `serverTime`, as `{ measure, beat }`: a 1-based measure and the quarter beats into it, wrapped
 * with the measure table (by default every measure has the state's time signature).
 */
export function positionAt(state, serverTime, table = measureTable(state)) {
  return table.measureAt(quarterBeatsAt(state, serverTime, table));
}

/**
 * A position in TabCanvas terms: a 0-based measure, and the beat in beats of that measure's time signature (in 6/8
 * a measure is 3 quarter beats but 6 eighth-note beats).
 */
export function toCanvasPosition(position, table) {
  const { beatUnit } = table.signatureOf(position.measure);
  return { measure: position.measure - 1, beat: (position.beat * beatUnit) / 4 };
}

/**
 * Whether a received state is newer than the applied one. Within a lesson, one whose version was already applied is
 * dropped, as messages can arrive after a newer snapshot. Versions count afresh in each lesson, so between lessons
 * the server time it was sent at decides: a state of another lesson is taken unless it is older than the applied
 * one (a late state of a lesson that ended while its end was missed).
 */
export function isNewerState(applied, incoming) {
  if (!incoming) return false;
  if (!applied) return true;
  if (applied.sessionId !== incoming.sessionId) return incoming.serverTime >= applied.serverTime;
  return incoming.version > applied.version;
}

/**
 * Whether the position jumped by more than MAX_DRIFT_MS between two frames, which playback alone never does.
 * `previous` and `current` are frames as `{ serverTime, localTime }`, where `localTime` is a monotonic clock
 * (performance.now()). Between two frames the server clock should move on exactly as far as the monotonic one,
 * however long the frame took; when it does not, the wall clock or the clock offset jumped (a changed system time, a
 * laptop waking up, the first clock sync of a badly set clock). The position is computed afresh from the state on
 * every frame, so it is right again at once; asking for a snapshot as well is cheap insurance that the state itself
 * is current after such an event. Missed states are caught by snapshots on reconnect and when the page comes back.
 */
export function needsResync(previous, current) {
  if (!previous) return false;
  const localElapsed = current.localTime - previous.localTime;
  const serverElapsed = current.serverTime - previous.serverTime;
  return Math.abs(serverElapsed - localElapsed) > MAX_DRIFT_MS;
}
