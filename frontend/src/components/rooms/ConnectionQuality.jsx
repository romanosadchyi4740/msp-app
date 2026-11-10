import { badgeClass } from './roomStyles';

const DOT = {
  good: 'bg-emerald-500',
  fair: 'bg-amber-500',
  poor: 'bg-red-500',
};

const figure = (value, unit) => (value === null || value === undefined ? 'not measured' : `${value} ${unit}`);

/**
 * How good this participant's audio connection to another one is (KAN-48, FR-13): a green, yellow or red dot with
 * the grade in words, the round trip, and a marker when the audio goes through the relay server. `peer` is the
 * rtc store's entry for that participant; nothing is shown until the connection is up and graded.
 */
export default function ConnectionQuality({ peer }) {
  if (!peer?.quality) return null;
  const details = `Round trip ${figure(peer.rttMs, 'ms')}, jitter ${figure(peer.jitterMs, 'ms')}, `
    + `packet loss ${figure(peer.lossPercent, '%')}`;
  return (
    <span className="flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400" title={details}>
      <span data-quality={peer.quality} className={`h-2.5 w-2.5 shrink-0 rounded-full ${DOT[peer.quality]}`} />
      <span>
        {peer.quality}
        {typeof peer.rttMs === 'number' && ` · ${peer.rttMs} ms`}
      </span>
      {peer.relay && <span className={badgeClass} title="Audio goes through the relay server">Relay</span>}
    </span>
  );
}
