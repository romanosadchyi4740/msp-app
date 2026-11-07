import socketClient from '../api/socketClient';
import usePlaybackStore from '../store/playbackStore';
import { serverNow, startClockSync, stopClockSync } from './clockSync';
import { needsResync, positionAt, toCanvasPosition } from './playbackMath';

/**
 * The client sync engine of a room (KAN-42): everyone sees the same position in the tab (UR-3).
 *
 * The server never streams positions. The engine keeps the room's playback state in the playback store, from the
 * room's state topic and from snapshots (always one on every connect, FR-10), and on every animation frame computes
 * the position from it at the synced server clock (positionAt(serverNow())). Whoever shows the position registers a
 * frame listener, e.g. the tab canvas calling setPosition.
 *
 * If the position may have drifted more than 150 ms from the expected one (the clock jumped), and when the page comes
 * back after being hidden or frozen (a tab switch, a sleeping laptop; states may have been missed meanwhile), the
 * engine sends REQUEST_SYNC and the server answers with a snapshot.
 *
 * One room at a time: the room page calls startSyncEngine(roomId) and the returned stop function when it leaves.
 */

/** At most one REQUEST_SYNC per this long, so a page that keeps stalling does not flood the server. */
const RESYNC_INTERVAL_MS = 2000;

let engine = null;
const frameListeners = new Set();

/**
 * Starts the engine for a room, stopping one that runs for another; returns the function that stops it.
 */
export function startSyncEngine(roomId) {
  engine?.stop();
  const store = usePlaybackStore.getState();
  store.reset(roomId);
  startClockSync();

  let subscriptions = [];
  let frame = null;
  let previousFrame = null;
  let lastResync = -Infinity;

  const unsubscribe = () => {
    subscriptions.forEach((subscription) => subscription?.unsubscribe());
    subscriptions = [];
  };

  const stopListening = socketClient.onConnect(() => {
    // A new connection starts without subscriptions; those of the old one went with it
    subscriptions = [
      socketClient.subscribe(`/topic/rooms/${roomId}/state`, (message) => {
        // Lesson changes go to the same topic; the playback states are the messages with a version
        if (typeof message.version === 'number') {
          usePlaybackStore.getState().applyState(message);
        } else if (message.type) {
          usePlaybackStore.getState().applyLessonEvent(message);
        }
      }),
      socketClient.subscribe(`/user/queue/rooms/${roomId}/snapshot`, applySnapshot),
      // Answered at once with a snapshot, so joining and every reconnect catch up
      socketClient.subscribe(`/app/rooms/${roomId}/snapshot`, applySnapshot),
    ];
  });

  const requestSync = (now) => {
    const { playback } = usePlaybackStore.getState();
    if (!playback || now - lastResync < RESYNC_INTERVAL_MS || !socketClient.isConnected()) return;
    lastResync = now;
    socketClient.sendMessage(`/rooms/${roomId}/command`, { type: 'REQUEST_SYNC', clientTime: Date.now() });
  };

  // A hidden page draws no frames, and a frozen one runs nothing at all, so their return is told by events
  const resyncOnReturn = () => {
    if (document.visibilityState === 'visible') {
      requestSync(performance.now());
    }
  };
  document.addEventListener('visibilitychange', resyncOnReturn);
  document.addEventListener('resume', resyncOnReturn);

  const onFrame = () => {
    frame = requestAnimationFrame(onFrame);
    const current = { serverTime: serverNow(), localTime: performance.now() };
    if (needsResync(previousFrame, current)) {
      requestSync(current.localTime);
    }
    previousFrame = current;
    const { playback } = usePlaybackStore.getState();
    if (!playback || frameListeners.size === 0) return;
    const position = toCanvasPosition(positionAt(playback, current.serverTime), playback);
    frameListeners.forEach((listener) => listener(position, playback));
  };
  frame = requestAnimationFrame(onFrame);

  const current = {
    stop: () => {
      if (engine !== current) return;
      engine = null;
      cancelAnimationFrame(frame);
      document.removeEventListener('visibilitychange', resyncOnReturn);
      document.removeEventListener('resume', resyncOnReturn);
      stopListening();
      unsubscribe();
      stopClockSync();
      usePlaybackStore.getState().reset(null);
    },
  };
  engine = current;
  return current.stop;
}

/**
 * Calls the listener on every animation frame while the room has a lesson, with the position in TabCanvas terms
 * (`{ measure, beat }`, 0-based measure) and the playback state; returns the function that removes it.
 */
export function onPlaybackFrame(listener) {
  frameListeners.add(listener);
  return () => frameListeners.delete(listener);
}

function applySnapshot(snapshot) {
  usePlaybackStore.getState().applySnapshot(snapshot);
}
