import toast from 'react-hot-toast';
import socketClient from '../api/socketClient';

/**
 * The host's playback commands (KAN-50): PLAY, PAUSE, SEEK, SET_TEMPO and METRONOME go to
 * `/app/rooms/{roomId}/command`; the server checks that the sender hosts the room (FR-8), and the new state comes
 * back on the room's state topic like everyone else's, so the controls show what the server applied, never what was
 * sent.
 *
 * A refused command is answered on `/user/queue/errors`, which carries no hint of what failed and is shared with the
 * signaling of the audio connections. An error is shown only when it comes within a few seconds of a command sent
 * from this browser, so the audio's errors do not pop up as if a button had failed.
 */

/** How long after a command an error on the queue is taken to be its answer. */
const ERROR_WINDOW_MS = 3000;

let lastCommandAt = -Infinity;

/**
 * Sends a command; returns false (and says so) when there is no connection to send it over.
 */
export function sendHostCommand(roomId, type, argument = null) {
  if (!socketClient.isConnected()) {
    toast.error('Not connected to the room; the command was not sent.');
    return false;
  }
  lastCommandAt = performance.now();
  socketClient.sendMessage(`/rooms/${roomId}/command`,
    { type, argument: argument === null ? null : String(argument), clientTime: Date.now() });
  return true;
}

/**
 * Shows the errors that answer this browser's commands while the host controls are on screen; returns the function
 * that stops it.
 */
export function watchCommandErrors() {
  let subscription = null;
  const stopListening = socketClient.onConnect(() => {
    subscription = socketClient.subscribe('/user/queue/errors', (error) => {
      if (performance.now() - lastCommandAt > ERROR_WINDOW_MS) return;
      toast.error(error?.message || 'The command was refused.');
    });
  });
  return () => {
    stopListening();
    subscription?.unsubscribe();
  };
}
