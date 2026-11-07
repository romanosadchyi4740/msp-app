import { useEffect, useState } from 'react';
import usePlaybackStore, { measureTableOf } from '../../store/playbackStore';
import { getClockSync, serverNow } from '../../sync/clockSync';
import { getSyncStats } from '../../sync/syncEngine';
import { positionAt } from '../../sync/playbackMath';

const REFRESH_MS = 250;

/**
 * The room's sync figures for debugging (KAN-44), shown with `?debug=1`: the clock offset and round trip, the
 * playback version and status, the position computed now, the largest jump of the server clock against the
 * monotonic one between two frames in the last 5 s (the engine asks for a snapshot above 150 ms) and how often it
 * asked. Refreshed a few times a second; the numbers are this participant's own; the drift between participants
 * is what the e2e test (e2e/syncDrift.spec.js) measures.
 */
export default function SyncDebugOverlay() {
  const [figures, setFigures] = useState(null);

  useEffect(() => {
    const read = () => {
      const state = usePlaybackStore.getState();
      const { playback } = state;
      const position = playback ? positionAt(playback, serverNow(), measureTableOf(state)) : null;
      setFigures({ clock: getClockSync(), playback, position, stats: getSyncStats() });
    };
    read();
    const timer = setInterval(read, REFRESH_MS);
    return () => clearInterval(timer);
  }, []);

  if (!figures) return null;
  const { clock, playback, position, stats } = figures;
  const rows = [
    ['offset', clock.synced ? `${clock.offset} ms` : 'not synced'],
    ['rtt', clock.rtt === null ? '–' : `${clock.rtt} ms`],
    ['version', playback ? `${playback.version}` : '–'],
    ['status', playback ? `${playback.status} @ ${playback.tempoBpm} bpm` : 'no lesson'],
    ['position', position ? `${position.measure}:${position.beat.toFixed(2)}` : '–'],
    ['clock jump', `${stats.maxDriftMs.toFixed(1)} ms (5 s max)`],
    ['resyncs', `${stats.resyncRequests}`],
  ];

  return (
    <aside
      aria-label="Sync debug"
      className="fixed bottom-4 right-4 z-50 border border-zinc-300 bg-white/90 p-3 font-mono text-[11px] text-zinc-900
        shadow-lg dark:border-zinc-700 dark:bg-black/90 dark:text-zinc-100"
    >
      <dl className="grid grid-cols-[auto_auto] gap-x-3 gap-y-0.5">
        {rows.map(([label, value]) => (
          <div key={label} className="contents">
            <dt className="text-zinc-500 dark:text-zinc-400">{label}</dt>
            <dd>{value}</dd>
          </div>
        ))}
      </dl>
    </aside>
  );
}
