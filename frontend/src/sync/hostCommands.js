import toast from 'react-hot-toast';
import socketClient from '../api/socketClient';

/**
 * The host's playback commands (KAN-50): PLAY, PAUSE, SEEK, SET_TEMPO and METRONOME go to
 * `/app/rooms/{roomId}/command`; the server checks that the sender hosts the room (FR-8), and the new state comes
 * back on the room's state topic like everyone else's, so the controls show what the server applied, never what was
 * sent.
 *
 * A refused command is answered on the sender's `/user/queue/rooms/{roomId}/command-errors` (KAN-96), a queue only
 * command errors use, so every error there is shown, however late it arrives, and the errors of the audio
 * connections' signaling or of the snapshot (on `/user/queue/errors`) never look like a failed button. The sync
 * engine's automatic REQUEST_SYNC is a command too, so in the rare case it fails (the room closing meanwhile) that
 * error shows here as well.
 */

/**
 * Sends a command; returns false (and says so) when there is no connection to send it over.
 */
export function sendHostCommand(roomId, type, argument = null) {
  if (!socketClient.isConnected()) {
    toast.error('Not connected to the room; the command was not sent.');
    return false;
  }
  socketClient.sendMessage(`/rooms/${roomId}/command`,
    { type, argument: argument === null ? null : String(argument), clientTime: Date.now() });
  return true;
}

/**
 * The queue the server answers this user's refused commands in the room on.
 */
export function commandErrorDestination(roomId) {
  return `/user/queue/rooms/${roomId}/command-errors`;
}

/**
 * Shows the errors that answer this user's commands in the room while the host controls are on screen; subscribes
 * again on every (re)connect and returns the function that stops it.
 */
export function watchCommandErrors(roomId) {
  let subscription = null;
  const stopListening = socketClient.onConnect(() => {
    subscription = socketClient.subscribe(commandErrorDestination(roomId), (error) => {
      toast.error(error?.message || 'The command was refused.');
    });
  });
  return () => {
    stopListening();
    subscription?.unsubscribe();
  };
}
