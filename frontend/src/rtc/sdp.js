/**
 * Session description helpers for the audio mesh (KAN-47). Pure text functions, so they are tested without a browser.
 */

/**
 * What the Opus encoder is asked for, so an acoustic guitar sounds like one: stereo, about 128 kbps instead of the
 * speech default, forward error correction against packet loss, and no discontinuous transmission, which would cut
 * off quiet, ringing notes.
 */
export const OPUS_MUSIC_PARAMS = {
  stereo: 1,
  'sprop-stereo': 1,
  maxaveragebitrate: 128000,
  useinbandfec: 1,
  usedtx: 0,
};

/** The packet length asked for: the shortest Opus frames WebRTC sends, for the least delay. */
export const PTIME_MS = 10;

/**
 * Rewrites a session description so that whoever applies it as the remote one sends music-friendly Opus: the
 * `OPUS_MUSIC_PARAMS` on every Opus payload type and `a=ptime` on the audio sections. Parameters the description
 * already has are kept unless they are overridden; other codecs and sections are untouched.
 */
export function tuneOpusForMusic(sdp) {
  if (typeof sdp !== 'string' || sdp === '') return sdp;
  const newline = sdp.includes('\r\n') ? '\r\n' : '\n';
  const lines = sdp.split(/\r?\n/);
  const result = [];
  let section = [];

  const flush = () => {
    result.push(...(section[0]?.startsWith('m=audio') ? tuneAudioSection(section) : section));
    section = [];
  };
  for (const line of lines) {
    if (line.startsWith('m=')) flush();
    section.push(line);
  }
  flush();
  return result.join(newline);
}

function tuneAudioSection(lines) {
  const opusTypes = lines
    .map((line) => /^a=rtpmap:(\d+) opus\/48000/i.exec(line)?.[1])
    .filter(Boolean);
  if (opusTypes.length === 0) return lines;

  const tuned = [];
  const withParameters = new Set();
  for (const line of lines) {
    if (line.startsWith('a=ptime:')) continue;
    const fmtp = /^a=fmtp:(\d+) (.*)$/.exec(line);
    if (fmtp && opusTypes.includes(fmtp[1])) {
      withParameters.add(fmtp[1]);
      tuned.push(`a=fmtp:${fmtp[1]} ${mergeParameters(fmtp[2])}`);
    } else {
      tuned.push(line);
    }
  }
  // An Opus payload type without parameters gets them right after its rtpmap line
  for (const type of opusTypes.filter((opusType) => !withParameters.has(opusType))) {
    const at = tuned.findIndex((line) => line.startsWith(`a=rtpmap:${type} `));
    tuned.splice(at + 1, 0, `a=fmtp:${type} ${mergeParameters('')}`);
  }
  // Before a trailing empty line, which is what is left of the description's final line break
  const end = tuned.at(-1) === '' ? tuned.length - 1 : tuned.length;
  tuned.splice(end, 0, `a=ptime:${PTIME_MS}`);
  return tuned;
}

function mergeParameters(existing) {
  const parameters = new Map();
  for (const part of existing.split(';')) {
    const [key, value] = part.trim().split('=');
    if (key) parameters.set(key, value);
  }
  for (const [key, value] of Object.entries(OPUS_MUSIC_PARAMS)) {
    parameters.set(key, String(value));
  }
  return [...parameters].map(([key, value]) => (value === undefined ? key : `${key}=${value}`)).join(';');
}

/**
 * The session id of a description's origin line (`o=- <session id> <version> ...`), or null if it has none. It
 * stays the same for every offer and answer of one RTCPeerConnection, so another id means the peer started over,
 * e.g. after reloading the page.
 */
export function sessionIdOf(sdp) {
  if (typeof sdp !== 'string') return null;
  return /^o=\S+ (\S+) /m.exec(sdp)?.[1] ?? null;
}
