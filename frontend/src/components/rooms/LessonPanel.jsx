import { useEffect, useRef, useState } from 'react';
import { FileMusic, Keyboard, Minus, Pause, Play, Plus, Square } from 'lucide-react';
import usePlaybackStore from '../../store/playbackStore';
import { onPlaybackFrame } from '../../sync/syncEngine';
import { sendHostCommand, watchCommandErrors } from '../../sync/hostCommands';
import {
  MAX_TEMPO_BPM, MIN_TEMPO_BPM, parseMeasure, shortcutCommand, stepTempo, TEMPO_STEP_BPM,
} from '../../sync/lessonControls';
import { inputClass } from '../tabs/tabDetails';
import MetronomeControls from './MetronomeControls';
import LessonTimer from './LessonTimer';
import { badgeClass, primaryButtonClass, secondaryButtonClass, sectionTitleClass } from './roomStyles';

const STATUS_LABELS = { PLAYING: 'Playing', PAUSED: 'Paused', STOPPED: 'Stopped' };
const bigButtonClass = 'h-14 min-w-14';

/**
 * The measure (1-based) at the shared position, updated only when it changes, not on every frame.
 */
function useCurrentMeasure() {
  const startMeasure = usePlaybackStore((state) => state.playback?.measure ?? 1);
  const [measure, setMeasure] = useState(null);
  useEffect(() => onPlaybackFrame((position) => {
    const next = position.measure + 1;
    setMeasure((before) => (before === next ? before : next));
  }), []);
  return measure ?? startMeasure;
}

/**
 * The lesson's controls (KAN-50): play and pause, go to a measure, the tempo, the metronome, the tab and ending the
 * lesson, with the time left. The host uses them, also from the keyboard (space, arrows); the participants see the
 * same, read-only. Everything shown comes from the state the server sent, so a command shows once it was applied.
 */
export default function LessonPanel({ roomId, lesson, isHost, onChangeTab, onEndLesson }) {
  const playback = usePlaybackStore((state) => state.playback);
  const measureCount = usePlaybackStore((state) => state.measureSignatures?.length ?? null);
  const measure = useCurrentMeasure();
  const [measureText, setMeasureText] = useState('');
  const send = (type, argument = null) => sendHostCommand(roomId, type, argument);

  const status = playback?.status ?? null;
  const playing = status === 'PLAYING';
  const tempoBpm = playback?.tempoBpm ?? null;
  const metronomeOn = Boolean(playback?.metronomeOn);
  const controlsOff = !isHost || !playback;

  useEffect(() => (isHost ? watchCommandErrors() : undefined), [isHost]);

  // The shortcuts read the latest values without listening again on every change
  const shortcutState = useRef(null);
  useEffect(() => {
    shortcutState.current = playback ? { status, measure, tempoBpm, measureCount } : null;
  }, [playback, status, measure, tempoBpm, measureCount]);
  useEffect(() => {
    if (!isHost) return undefined;
    const onKeyDown = (event) => {
      // Dialogs (picking a tab, confirming) have the keys to themselves
      if (document.querySelector('[aria-modal="true"]')) return;
      const command = shortcutCommand(event, shortcutState.current);
      if (!command) return;
      event.preventDefault();
      sendHostCommand(roomId, command.type, command.argument);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [isHost, roomId]);

  const goToMeasure = (event) => {
    event.preventDefault();
    const target = parseMeasure(measureText, measureCount);
    if (target === null) return;
    if (send('SEEK', target)) setMeasureText('');
  };
  const measureInvalid = measureText.trim() !== '' && parseMeasure(measureText, measureCount) === null;

  // A clicked button keeps the focus, and Space would press it again (a second "Faster") instead of playing or
  // pausing. Mouse clicks (detail > 0) let go of the focus; buttons pressed from the keyboard keep it
  const releaseClickedButton = (event) => {
    if (isHost && event.detail > 0) event.target.closest?.('button')?.blur();
  };

  return (
    <section className="space-y-5" aria-label="Lesson controls" onClick={releaseClickedButton}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 className={sectionTitleClass}>Lesson</h2>
        <LessonTimer endsAt={lesson.endsAt} />
      </div>

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => send(playing ? 'PAUSE' : 'PLAY')}
          disabled={controlsOff}
          aria-label={playing ? 'Pause' : 'Play'}
          title={isHost ? `${playing ? 'Pause' : 'Play'} (space)` : undefined}
          className={`${primaryButtonClass} ${bigButtonClass}`}
        >
          {playing ? <Pause size={22} /> : <Play size={22} />}
        </button>
        <div className="min-w-0">
          <p className="text-lg font-black tabular-nums" aria-live="off">
            Measure {measure}{measureCount ? ` of ${measureCount}` : ''}
          </p>
          <p className="text-xs text-zinc-500 dark:text-zinc-400">{STATUS_LABELS[status] ?? 'Waiting for the playback'}</p>
        </div>
      </div>

      {isHost && (
        <form onSubmit={goToMeasure} className="flex items-center gap-2">
          <input
            className={`${inputClass} w-28`}
            inputMode="numeric"
            placeholder="Measure"
            aria-label="Go to measure"
            aria-invalid={measureInvalid}
            value={measureText}
            onChange={(event) => setMeasureText(event.target.value)}
            disabled={controlsOff}
          />
          <button type="submit" disabled={controlsOff || measureText.trim() === '' || measureInvalid} className={secondaryButtonClass}>
            Go
          </button>
          {measureInvalid && (
            <span className="text-xs text-red-600 dark:text-red-400">
              {measureCount ? `1 to ${measureCount}` : 'From 1'}
            </span>
          )}
        </form>
      )}

      <div className="flex items-center gap-3">
        <button
          type="button"
          onClick={() => send('SET_TEMPO', stepTempo(tempoBpm, -TEMPO_STEP_BPM))}
          disabled={controlsOff || tempoBpm <= MIN_TEMPO_BPM}
          aria-label="Slower"
          title={isHost ? `${TEMPO_STEP_BPM} BPM slower (down arrow)` : undefined}
          className={`${secondaryButtonClass} ${bigButtonClass}`}
        >
          <Minus size={18} />
        </button>
        <p className="min-w-24 text-center">
          <span className="block text-2xl font-black tabular-nums" data-tempo>{tempoBpm ?? '–'}</span>
          <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">BPM</span>
        </p>
        <button
          type="button"
          onClick={() => send('SET_TEMPO', stepTempo(tempoBpm, TEMPO_STEP_BPM))}
          disabled={controlsOff || tempoBpm >= MAX_TEMPO_BPM}
          aria-label="Faster"
          title={isHost ? `${TEMPO_STEP_BPM} BPM faster (up arrow)` : undefined}
          className={`${secondaryButtonClass} ${bigButtonClass}`}
        >
          <Plus size={18} />
        </button>
      </div>

      <div className="space-y-3">
        {isHost && (
          <button
            type="button"
            onClick={() => send('METRONOME', !metronomeOn)}
            disabled={controlsOff}
            aria-pressed={metronomeOn}
            className={metronomeOn ? primaryButtonClass : secondaryButtonClass}
          >
            Metronome {metronomeOn ? 'on' : 'off'}
          </button>
        )}
        <MetronomeControls showState={!isHost} />
      </div>

      {isHost ? (
        <div className="flex flex-wrap gap-3 border-t border-zinc-200 pt-5 dark:border-zinc-800">
          <button type="button" onClick={onChangeTab} className={secondaryButtonClass}>
            <FileMusic size={14} /> Change tab
          </button>
          <button type="button" onClick={onEndLesson} className={secondaryButtonClass}>
            <Square size={14} /> End lesson
          </button>
          <p className="flex w-full items-start gap-2 text-xs text-zinc-500 dark:text-zinc-400">
            <Keyboard size={14} className="mt-0.5 shrink-0" />
            Space plays and pauses, left and right go a measure back and forward, up and down change the tempo.
          </p>
        </div>
      ) : (
        <p className="text-xs text-zinc-500 dark:text-zinc-400">
          <span className={badgeClass}>Host controls</span> The host plays, pauses and sets the tempo for everyone.
        </p>
      )}
    </section>
  );
}
