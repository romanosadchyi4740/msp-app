/**
 * The pure parts of the host's lesson controls (KAN-50): tempo and measure bounds, the keyboard shortcuts and the
 * lesson time left. The server checks every command again; these only keep the controls from sending what it would
 * refuse.
 */

/** The tempo range the server accepts (PlaybackStates.MIN_TEMPO_BPM / MAX_TEMPO_BPM). */
export const MIN_TEMPO_BPM = 20;
export const MAX_TEMPO_BPM = 400;
/** How much one tempo step (the −/+ buttons, the up and down arrows) changes the tempo. */
export const TEMPO_STEP_BPM = 5;

/**
 * The tempo `delta` beats per minute away from `tempoBpm`, within the accepted range.
 */
export function stepTempo(tempoBpm, delta) {
  return Math.min(MAX_TEMPO_BPM, Math.max(MIN_TEMPO_BPM, Math.round(tempoBpm + delta)));
}

/**
 * The measure (1-based) typed into "go to measure", or null if it is not one: a whole number from 1, and at most
 * `measureCount` when the tab's measures are known.
 */
export function parseMeasure(text, measureCount = null) {
  const trimmed = String(text ?? '').trim();
  if (!/^\d+$/.test(trimmed)) return null;
  const measure = Number(trimmed);
  if (measure < 1 || (measureCount && measure > measureCount)) return null;
  return measure;
}

/**
 * The measure (1-based) `delta` measures away from `measure`, from 1 to `measureCount` when it is known.
 */
export function stepMeasure(measure, delta, measureCount = null) {
  const next = Math.max(1, measure + delta);
  return measureCount ? Math.min(measureCount, next) : next;
}

const TYPING_TAGS = new Set(['INPUT', 'TEXTAREA', 'SELECT']);

/**
 * The host command a key press stands for, as `{ type, argument }`, or null if the key is not a shortcut or is meant
 * for something else (typing in a field, a button that space presses, a key with a modifier or held down):
 * space plays or pauses, left and right go one measure back or forward, up and down change the tempo by a step.
 *
 * `lesson` is `{ status, measure, tempoBpm, measureCount }`: the playback status, the current measure (1-based),
 * the tempo, and the tab's measure count when it is known.
 */
export function shortcutCommand(event, lesson) {
  // A held key repeats some 30 times a second; every repeat would be a command the server saves and sends to everyone
  if (!lesson || event.repeat || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return null;
  const target = event.target;
  if (target && (TYPING_TAGS.has(target.tagName) || target.isContentEditable)) return null;
  switch (event.key) {
    case ' ':
      // Space on a focused button presses it; the shortcut would press it a second time
      if (target?.tagName === 'BUTTON') return null;
      return { type: lesson.status === 'PLAYING' ? 'PAUSE' : 'PLAY', argument: null };
    case 'ArrowLeft':
    case 'ArrowRight': {
      const measure = stepMeasure(lesson.measure, event.key === 'ArrowLeft' ? -1 : 1, lesson.measureCount);
      return measure === lesson.measure ? null : { type: 'SEEK', argument: String(measure) };
    }
    case 'ArrowUp':
    case 'ArrowDown': {
      const tempo = stepTempo(lesson.tempoBpm, event.key === 'ArrowUp' ? TEMPO_STEP_BPM : -TEMPO_STEP_BPM);
      return tempo === lesson.tempoBpm ? null : { type: 'SET_TEMPO', argument: String(tempo) };
    }
    default:
      return null;
  }
}

/**
 * The lesson time left at `serverTime` (epoch ms) as `m:ss` (or `h:mm:ss`), never below zero; null for a lesson
 * without a limit.
 */
export function timeLeft(endsAt, serverTime) {
  if (!endsAt) return null;
  const totalSeconds = Math.max(0, Math.ceil((Date.parse(endsAt) - serverTime) / 1000));
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${String(minutes).padStart(2, '0')}:${seconds}` : `${minutes}:${seconds}`;
}
