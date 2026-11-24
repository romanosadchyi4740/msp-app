// The pure parts of listening broadcasts (KAN-109). The server keeps each broadcast's state and stamps it with its
// own time (anchorTime) and a version; a listener computes where the host is from that anchor and the synced server
// clock, as SyncChord rooms do, and corrects its player when it is too far off.

/** How far a listener may be from the host before the player seeks, in seconds. Music tolerates less than a second. */
export const DRIFT_THRESHOLD_SECONDS = 1;

/** How often the host re-anchors while playing, so its own buffering stalls do not add up on the listeners. */
export const HOST_REANCHOR_MS = 10_000;

/**
 * Whether a state from the server should be applied: a newer version, or the end of the broadcast, which always is.
 */
export function isNewer(state, lastVersion) {
  if (!state) return false;
  if (state.status === 'TERMINATED') return true;
  return lastVersion == null || state.version > lastVersion;
}

/**
 * Where the host is now, in seconds: the anchored position, advanced by the time since the anchor while it plays.
 * Never before the anchor, even if the clocks disagree a little.
 */
export function expectedPosition(state, serverNowMs) {
  if (!state) return 0;
  if (!state.playing) return state.position;
  return state.position + Math.max(0, serverNowMs - state.anchorTime) / 1000;
}

/**
 * The position to seek to, or null when the player is close enough. A known duration caps it, so the end of a
 * track is not overshot while the host's next track is on its way.
 */
export function seekTarget(actualSeconds, expectedSeconds, durationSeconds, threshold = DRIFT_THRESHOLD_SECONDS) {
  const target = Number.isFinite(durationSeconds) && durationSeconds > 0
    ? Math.min(expectedSeconds, Math.max(0, durationSeconds - 0.25))
    : expectedSeconds;
  return Math.abs(actualSeconds - target) > threshold ? target : null;
}

/**
 * Whether a listener's player should play: the host plays and the listener has not paused it locally.
 */
export function shouldPlay(state, held) {
  return Boolean(state && state.status === 'ACTIVE' && state.trackId && state.playing && !held);
}

/**
 * What the host sends with START and UPDATE: its track, where it is and whether it plays.
 */
export function hostPlayback(trackId, audio) {
  const position = audio && Number.isFinite(audio.currentTime) ? audio.currentTime : 0;
  return {
    trackId: trackId ?? null,
    position: Math.max(0, position),
    playing: Boolean(trackId && audio && !audio.paused),
  };
}
