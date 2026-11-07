import { Volume2, VolumeX } from 'lucide-react';
import useMetronomeStore from '../../store/metronomeStore';
import usePlaybackStore from '../../store/playbackStore';
import { enableMetronomeSound } from '../../sync/metronome';
import { badgeClass, secondaryButtonClass } from './roomStyles';

/**
 * This participant's side of the shared metronome: whether the host has it on, the button browsers require before
 * any sound, and the local volume. The host switches the metronome itself on and off.
 */
export default function MetronomeControls() {
  const metronomeOn = usePlaybackStore((state) => Boolean(state.playback?.metronomeOn));
  const audioState = useMetronomeStore((state) => state.audioState);
  const volume = useMetronomeStore((state) => state.volume);
  const setVolume = useMetronomeStore((state) => state.setVolume);

  return (
    <div className="flex flex-wrap items-center gap-3">
      <span className={badgeClass}>Metronome {metronomeOn ? 'on' : 'off'}</span>
      {audioState === 'suspended' ? (
        <button type="button" onClick={() => enableMetronomeSound()} className={secondaryButtonClass}>
          <Volume2 size={14} /> Enable sound
        </button>
      ) : (
        <label className="flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400">
          {volume === 0 ? <VolumeX size={14} /> : <Volume2 size={14} />}
          <span className="sr-only">Metronome volume</span>
          <input
            type="range"
            min="0"
            max="1"
            step="0.05"
            value={volume}
            onChange={(event) => setVolume(event.target.value)}
            aria-label="Metronome volume"
            className="w-28 accent-zinc-900 dark:accent-white"
          />
        </label>
      )}
    </div>
  );
}
