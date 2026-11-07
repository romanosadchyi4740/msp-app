import { create } from 'zustand';
import { isNewerState } from '../sync/playbackMath.js';

/**
 * The lesson playback of the room page that is open (KAN-42), as the server last sent it; fed by the sync engine
 * from the room's state topic and from snapshots. It holds the state, never a position: the position is computed
 * from it on every frame (playbackMath.positionAt).
 *
 * - `playback`: the server's PlaybackStateDto, or null while the room has no active lesson.
 * - `lessonEvent`: the last lesson change (LESSON_STARTED, TAB_CHANGED, LIMIT_WARNING, LESSON_ENDED) of the room.
 * - `endedSessions`: lessons that ended while the page was open; their states are dropped, since the final state
 *   of a lesson can arrive after the event that ended it.
 */
const usePlaybackStore = create((set, get) => ({
  roomId: null,
  playback: null,
  lessonEvent: null,
  endedSessions: [],

  /**
   * Starts afresh for a room: nothing applied yet.
   */
  reset: (roomId) => set({ roomId, playback: null, lessonEvent: null, endedSessions: [] }),

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
   * a room without a lesson clears it; snapshots of another room are ignored.
   */
  applySnapshot: (snapshot) => {
    if (!snapshot || snapshot.roomId !== get().roomId) return false;
    if (!snapshot.playback) {
      if (get().playback === null) return false;
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

export default usePlaybackStore;
