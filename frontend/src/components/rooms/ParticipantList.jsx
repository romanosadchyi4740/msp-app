import { Link } from 'react-router-dom';
import { Crown, User } from 'lucide-react';
import useRtcStore from '../../store/rtcStore';
import ConnectionQuality from './ConnectionQuality';
import { badgeClass, sectionTitleClass } from './roomStyles';

/**
 * What the list says about the audio connection to a participant.
 */
function audioLabel(participant, peer) {
  if (peer?.state === 'connected') return 'audio connected';
  if (!participant.online) return 'offline';
  if (peer?.state === 'failed' || peer?.state === 'disconnected') return 'reconnecting audio';
  return 'connecting audio';
}

/**
 * Who is in the room: role, whether they have the room open, the audio connection to them and how good it is
 * (KAN-47, KAN-48).
 */
export default function ParticipantList({ participants, capacity, userId }) {
  const audioPeers = useRtcStore((state) => state.peers);

  return (
    <section className="space-y-3">
      <h2 className={sectionTitleClass}>Participants · {participants.length} of {capacity}</h2>
      <ul className="space-y-2">
        {participants.map((participant) => {
          const me = participant.userId === userId;
          return (
            <li key={participant.userId} className="space-y-1 border border-zinc-200 p-3 dark:border-zinc-800">
              <div className="flex items-center gap-3">
                {participant.role === 'HOST'
                  ? <Crown size={18} className="shrink-0" aria-label="Host" />
                  : <User size={18} className="shrink-0 text-zinc-600 dark:text-zinc-400" />}
                <span
                  className={`h-2 w-2 shrink-0 rounded-full ${participant.online || me ? 'bg-emerald-500' : 'bg-zinc-400'}`}
                  title={participant.online || me ? 'Has the room open' : 'Offline'}
                />
                <Link to={`/users/${participant.userId}`} className="min-w-0 flex-1 truncate text-sm font-bold hover:underline">
                  {participant.displayName || 'Unknown'}
                </Link>
                {me && <span className="text-xs text-zinc-600 dark:text-zinc-400">you</span>}
                {participant.role === 'HOST' && <span className={badgeClass}>Host</span>}
              </div>
              {/* The last report of someone who closed the room says nothing about now */}
              {(!me || (participant.online && typeof participant.rttMs === 'number')) && (
                <div className="flex flex-wrap items-center gap-3 pl-[2.6rem] text-xs text-zinc-600 dark:text-zinc-400">
                  {!me && <span>{audioLabel(participant, audioPeers[participant.userId])}</span>}
                  {!me && <ConnectionQuality peer={audioPeers[participant.userId]} />}
                  {participant.online && typeof participant.rttMs === 'number' && (
                    <span title="This participant's average round trip to the others, as their browser reported it">
                      avg {participant.rttMs} ms
                    </span>
                  )}
                </div>
              )}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
