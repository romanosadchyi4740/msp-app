/**
 * What the device check (KAN-49, UR-2, FR-6) decides without a browser: whether the browser can take part in a
 * room's audio, what to tell the user when something is missing, how loud the microphone is, and which devices to
 * offer. Pure, so it is tested with node.
 */

/**
 * What a room needs from the browser, read from `env` (the page passes `window`; tests pass a plain object).
 *
 * - `secureContext`: browsers hand out the microphone only on https or localhost.
 * - `webrtc`: RTCPeerConnection, for hearing the others.
 * - `microphoneApi`: navigator.mediaDevices.getUserMedia.
 * - `webAudio`: AudioContext, for the metronome and the level meter.
 * - `outputSelection`: whether the sound can be sent to a chosen output device (optional: without it the browser
 *   plays on the system's default output).
 */
export function browserSupport(env) {
  const mediaDevices = env?.navigator?.mediaDevices;
  return {
    secureContext: env?.isSecureContext !== false,
    webrtc: typeof env?.RTCPeerConnection === 'function',
    microphoneApi: typeof mediaDevices?.getUserMedia === 'function',
    webAudio: typeof (env?.AudioContext ?? env?.webkitAudioContext) === 'function',
    outputSelection: typeof env?.HTMLMediaElement?.prototype?.setSinkId === 'function',
  };
}

/** The checks a room cannot do without, in the order they are shown, with what to do when one fails. */
export const REQUIRED_CHECKS = [
  {
    id: 'secureContext',
    label: 'Secure connection',
    hint: 'Open this site with an address that starts with https://. Browsers give the microphone only to secure '
      + 'pages.',
  },
  {
    id: 'webrtc',
    label: 'Live audio between participants (WebRTC)',
    hint: 'This browser cannot connect you to the others. Use a current version of Chrome, Edge, Firefox or '
      + 'Safari, and switch off extensions or privacy settings that block WebRTC.',
  },
  {
    id: 'microphoneApi',
    label: 'Microphone access',
    hint: 'This browser does not let pages use the microphone. Use a current version of Chrome, Edge, Firefox or '
      + 'Safari; inside another app (a messenger or mail app), open the link in the browser itself.',
  },
  {
    id: 'webAudio',
    label: 'Metronome sound (Web Audio)',
    hint: 'This browser cannot play the shared metronome. Update it, or use a current version of Chrome, Edge, '
      + 'Firefox or Safari.',
  },
];

/**
 * The required checks with their outcome, as `{ id, label, hint, ok }`.
 */
export function requiredChecks(support) {
  return REQUIRED_CHECKS.map((check) => ({ ...check, ok: Boolean(support[check.id]) }));
}

/** Whether the browser can take part in a room's audio at all. */
export function isCompatible(support) {
  return requiredChecks(support).every((check) => check.ok);
}

/**
 * What to tell the user when the microphone could not be opened, by the name of the error `getUserMedia` threw:
 * `{ message, hint }`.
 */
export function microphoneProblem(errorName) {
  switch (errorName) {
    case 'NotAllowedError':
    case 'SecurityError':
      return {
        message: 'The browser did not allow the microphone.',
        hint: 'Click the lock or microphone icon in the address bar, allow the microphone for this site, and check '
          + 'again. On a Mac or on Windows the system settings may also have to allow it for the browser.',
      };
    case 'NotFoundError':
    case 'OverconstrainedError':
      return {
        message: 'No microphone was found.',
        hint: 'Plug in a microphone or a headset, or choose another one, and check again.',
      };
    case 'NotReadableError':
    case 'AbortError':
      return {
        message: 'The microphone is used by another application.',
        hint: 'Close the other programs that use it (a call, a recorder) and check again.',
      };
    default:
      return {
        message: 'Could not turn the microphone on.',
        hint: 'Check that a microphone is connected and allowed for this site, and check again.',
      };
  }
}

/** The quietest level the meter shows; anything below is silence. */
const FLOOR_DB = -60;

/**
 * How loud a block of samples (each -1 to 1, as an AnalyserNode gives them) is, as a meter value from 0 (silence,
 * -60 dB or less) to 1 (full scale). The scale is in decibels, as hearing is: a quiet guitar still moves the meter.
 */
export function levelOf(samples) {
  if (!samples || samples.length === 0) return 0;
  let sum = 0;
  for (const sample of samples) {
    sum += sample * sample;
  }
  const rms = Math.sqrt(sum / samples.length);
  if (rms <= 0) return 0;
  const db = 20 * Math.log10(rms);
  return Math.min(1, Math.max(0, (db - FLOOR_DB) / -FLOOR_DB));
}

/** Above this the meter counts as "the microphone hears something". */
export const HEARD_LEVEL = 0.25;

/**
 * The microphones and outputs to offer, from `navigator.mediaDevices.enumerateDevices()`: `{ inputs, outputs }`,
 * each a list of `{ deviceId, label }`. Before the microphone is allowed browsers give no names (and Firefox no
 * outputs at all), so a device without a name gets a numbered one; entries without an id are left out.
 */
export function groupDevices(devices) {
  const of = (kind, name) => (devices ?? [])
    .filter((device) => device.kind === kind && device.deviceId)
    .map((device, index) => ({ deviceId: device.deviceId, label: device.label || `${name} ${index + 1}` }));
  return { inputs: of('audioinput', 'Microphone'), outputs: of('audiooutput', 'Output') };
}

/**
 * The saved device if it is still among the available ones, otherwise '' (the system's default): a headset that
 * was unplugged must not leave the room without sound.
 */
export function availableDevice(savedId, devices) {
  return savedId && devices.some((device) => device.deviceId === savedId) ? savedId : '';
}
