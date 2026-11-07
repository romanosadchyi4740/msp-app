import usePlaybackStore from '../store/playbackStore';
import useMetronomeStore from '../store/metronomeStore';
import { serverNow } from './clockSync';
import { audioTimeFor, clicksBetween, withoutPlayed } from './metronomeMath';

/**
 * The shared metronome of the open room (KAN-43, FR-9): clicks on the Web Audio clock, derived from the lesson's
 * playback state and the synced server clock, so every participant hears the same beat.
 *
 * Clicks are never timed with timers. A lookahead scheduler wakes every TICK_MS and schedules the clicks of the next
 * LOOKAHEAD_MS on the AudioContext clock, which is sample-accurate; the timer only has to wake often enough. When
 * the playback state changes (play, pause, seek, tempo, metronome on/off), the clicks not yet heard are cancelled
 * and planned again from the new state.
 *
 * Browsers only allow sound after a user gesture: until then the context is suspended (useMetronomeStore's
 * audioState) and enableMetronomeSound() has to be called from a click.
 */

const TICK_MS = 25;
/** How far ahead clicks are scheduled, on top of the device's output latency (Bluetooth headphones: 150-250 ms). */
const LOOKAHEAD_MS = 100;
/**
 * A hidden page's timers wake about once a second, so it schedules this far ahead; a state change cancels whatever
 * was scheduled and not yet started, so planning far ahead costs nothing.
 */
const HIDDEN_LOOKAHEAD_MS = 1500;
/**
 * State messages arrive after the server applied them, so a click right at a new anchor (a seek lands on the
 * downbeat) is already due. Clicks this late are still played, at once, rather than dropped; later ones are skipped.
 */
const LATE_MS = 50;
const CLICK_LENGTH_S = 0.05;

let context = null;
let master = null;
let timer = null;
let unsubscribers = [];
// The clicks scheduled and not cancelled, as { time, at, oscillator, envelope }; kept a little after they sounded,
// for de-duplication
let scheduled = [];
// Clicks after this server time are not planned yet
let plannedUntil = 0;

/**
 * Starts the metronome for the open room; returns the function that stops it. Creating the AudioContext needs no
 * user gesture; it starts suspended until enableMetronomeSound().
 */
export function startMetronome() {
  stopMetronome();
  const AudioContextClass = window.AudioContext || window.webkitAudioContext;
  if (!AudioContextClass) return stopMetronome;
  context = new AudioContextClass();
  master = context.createGain();
  master.gain.value = useMetronomeStore.getState().volume;
  master.connect(context.destination);
  context.onstatechange = () => useMetronomeStore.getState().setAudioState(context?.state ?? null);
  useMetronomeStore.getState().setAudioState(context.state);
  plannedUntil = serverNow() - LATE_MS;

  unsubscribers = [
    usePlaybackStore.subscribe((state, previous) => {
      if (state.playback !== previous.playback) replan();
    }),
    useMetronomeStore.subscribe((state) => {
      if (master) master.gain.setTargetAtTime(state.volume, context.currentTime, 0.01);
    }),
  ];
  timer = setInterval(schedule, TICK_MS);
  return stopMetronome;
}

export function stopMetronome() {
  clearInterval(timer);
  timer = null;
  unsubscribers.forEach((unsubscribe) => unsubscribe());
  unsubscribers = [];
  scheduled = [];
  if (context) {
    context.onstatechange = null;
    context.close().catch(() => {});
  }
  context = null;
  master = null;
  useMetronomeStore.getState().setAudioState(null);
}

/**
 * Lets the metronome sound; call it from a user gesture (a click), as browsers require.
 */
export function enableMetronomeSound() {
  return context?.resume();
}

/**
 * The clicks not started yet belong to the old state: cancel them and plan from now on with the new one. Clicks
 * already sounding (or heard) stay, so the new plan does not play them again.
 */
function replan() {
  if (!context) return;
  const now = serverNow();
  scheduled = scheduled.filter((click) => {
    if (click.at <= context.currentTime) return true;
    click.oscillator.stop(0);
    click.oscillator.disconnect();
    click.envelope.disconnect();
    return false;
  });
  plannedUntil = now - LATE_MS;
  schedule();
}

function schedule() {
  if (!context || context.state !== 'running') return;
  const now = serverNow();
  const latencyMs = 1000 * (context.outputLatency || context.baseLatency || 0);
  const until = now + latencyMs + (document.hidden ? HIDDEN_LOOKAHEAD_MS : LOOKAHEAD_MS);
  const { playback } = usePlaybackStore.getState();
  const from = Math.max(plannedUntil, now - LATE_MS);
  if (playback && until > from) {
    const planned = scheduled.map((click) => click.time);
    const clicks = withoutPlayed(clicksBetween(playback, from, until), planned, now);
    const timestamp = outputTimestamp();
    const serverMinusPerformance = now - performance.now();
    clicks.forEach((click) => {
      const at = Math.max(context.currentTime, audioTimeFor(click.time, timestamp, serverMinusPerformance));
      scheduled.push({ time: click.time, at, ...playClick(at, click) });
    });
  }
  plannedUntil = Math.max(plannedUntil, until);
  // Heard clicks are kept a little longer than the late window, for de-duplication after a re-plan
  scheduled = scheduled.filter((click) => click.time > now - 2 * LATE_MS || click.at > context.currentTime);
}

function outputTimestamp() {
  const timestamp = context.getOutputTimestamp?.();
  // Some browsers report zeros until the first audio has been output
  if (timestamp && timestamp.performanceTime > 0) return timestamp;
  return { contextTime: context.currentTime, performanceTime: performance.now() };
}

/**
 * A short blip: higher on the downbeat, a little louder in the count-in. Returns its nodes, to cancel it.
 */
function playClick(at, { accent, countIn }) {
  const oscillator = context.createOscillator();
  const envelope = context.createGain();
  oscillator.frequency.value = accent ? 1600 : 1000;
  const peak = countIn ? 1 : 0.7;
  envelope.gain.setValueAtTime(0.0001, at);
  envelope.gain.exponentialRampToValueAtTime(peak, at + 0.002);
  envelope.gain.exponentialRampToValueAtTime(0.0001, at + CLICK_LENGTH_S);
  oscillator.connect(envelope);
  envelope.connect(master);
  oscillator.start(at);
  oscillator.stop(at + CLICK_LENGTH_S);
  oscillator.onended = () => {
    oscillator.disconnect();
    envelope.disconnect();
  };
  return { oscillator, envelope };
}
