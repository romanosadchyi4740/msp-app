import { create } from 'zustand';
import { isNewerState, measureTable } from '../sync/playbackMath.js';

/**
 * The lesson playback of the room page that is open (KAN-42), as the server last sent it; fed by the sync engine
 * from the room's state topic and from snapshots. It holds the state, never a position: the position is computed
 * from it on every frame (playbackMath.positionAt).
 *
 * - `playback`: the server's PlaybackStateDto, or null while the room has no active lesson.
 * - `lessonEvent`: the last lesson change (LESSON_STARTED, TAB_CHANGED, LIMIT_WARNING, LESSON_ENDED) of the room.
 * - `endedSessions`: lessons that ended while the page was open; their states are dropped, since the final state
 *   of a lesson can arrive after the event that ended it.
 * - `snapshotCount`: grows with every snapshot of the room. Lesson events sent while the connection was down are not
 *   replayed, so whoever shows the lesson itself (its tab) loads it again on each snapshot.
 * - `measureSignatures`: every measure's time signature of the lesson's tab as rendered here, `{ beatsPerMeasure,
 *   beatUnit }` in order, or null while it is not loaded. The server does not know them (a Guitar Pro tab can change
 *   time signature anywhere), so positions are wrapped into measures with them (KAN-95); read the measure table with
 *   measureTableOf.
 */
const usePlaybackStore = create((set, get) => ({
  roomId: null,
  playback: null,
  lessonEvent: null,
  endedSessions: [],
  snapshotCount: 0,
  measureSignatures: null,

  /**
   * Starts afresh for a room: nothing applied yet.
   */
  reset: (roomId) => set({
    roomId, playback: null, lessonEvent: null, endedSessions: [], snapshotCount: 0, measureSignatures: null,
  }),

  setMeasureSignatures: (measureSignatures) => set({ measureSignatures: measureSignatures?.length ? measureSignatures : null }),

  /**
   * A state from the room's topic; returns whether it was applied. Already applied versions are dropped.
   */
  applyState: (state) => {
    if (!isNewerState(get().playback, state) || get().endedSessions.includes(state.sessionId)) return false;
    set({ playback: state });
    return true;
  },

  /**
   * A snapshot (on joining, reconnecting and REQUEST_SYNC); returns whether it changed the playback. A snapshot of
   * a room without a lesson clears it, unless it was taken before the applied state (a lesson started while it was
   * on its way); snapshots of another room are ignored.
   */
  applySnapshot: (snapshot) => {
    if (!snapshot || snapshot.roomId !== get().roomId) return false;
    set({ snapshotCount: get().snapshotCount + 1 });
    if (!snapshot.playback) {
      const { playback } = get();
      if (playback === null || snapshot.serverTime < playback.serverTime) return false;
      set({ playback: null });
      return true;
    }
    return get().applyState(snapshot.playback);
  },

  /**
   * A lesson change from the room's topic. An ended lesson has no playback any more.
   */
  applyLessonEvent: (event) => {
    if (event.type !== 'LESSON_ENDED') {
      set({ lessonEvent: event });
      return;
    }
    const { playback, endedSessions } = get();
    set({
      lessonEvent: event,
      endedSessions: [...endedSessions, event.sessionId],
      ...(playback?.sessionId === event.sessionId ? { playback: null } : {}),
    });
  },
}));

let cachedTable = { signature: null, signatures: null, table: null };

/**
 * The measure table of the store's state (playbackMath.measureTable): the rendered tab's measures, or the playback's
 * time signature for all while the tab is not loaded; null without playback. Built once per change, as the sync
 * engine and the metronome read it many times a second.
 */
export function measureTableOf({ playback, measureSignatures }) {
  if (!playback) return null;
  const signature = `${playback.beatsPerMeasure}/${playback.beatUnit}`;
  if (cachedTable.signature !== signature || cachedTable.signatures !== measureSignatures) {
    cachedTable = { signature, signatures: measureSignatures, table: measureTable(playback, measureSignatures) };
  }
  return cachedTable.table;
}

export default usePlaybackStore;
