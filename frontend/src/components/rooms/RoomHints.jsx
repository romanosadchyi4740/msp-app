import { Link } from 'react-router-dom';
import { Lightbulb, X } from 'lucide-react';
import useUiStore from '../../store/uiStore';
import { roomHints } from './firstRoomHints';
import { primaryButtonClass } from './roomStyles';

/**
 * Hints for a user's first room visit (KAN-53), for the host or a participant. Shown until dismissed; dismissing
 * is remembered in this browser per room role, for every room.
 */
export default function RoomHints({ isHost, roomPath }) {
  const role = isHost ? 'host' : 'participant';
  const seen = useUiStore((state) => Boolean(state.roomHintsSeen?.[role]));
  const dismissRoomHints = useUiStore((state) => state.dismissRoomHints);
  if (seen) return null;
  const dismiss = () => dismissRoomHints(role);

  return (
    <section
      aria-labelledby="room-hints-title"
      className="relative border border-amber-500/60 bg-amber-50 p-6 text-zinc-900 dark:border-amber-400/40 dark:bg-zinc-900 dark:text-zinc-100"
    >
      <button
        type="button"
        onClick={dismiss}
        aria-label="Hide the hints"
        className="absolute right-4 top-4 text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white"
      >
        <X size={16} />
      </button>
      <h2 id="room-hints-title" className="flex items-center gap-2 text-sm font-black">
        <Lightbulb size={16} className="text-amber-600 dark:text-amber-400" />
        {isHost ? 'Your first room as host' : 'Welcome to the room'}
      </h2>
      <ol className="mt-4 grid gap-4 md:grid-cols-3">
        {roomHints({ isHost, roomPath }).map((hint, index) => (
          <li key={hint.id} className="space-y-1">
            <p className="text-xs font-black uppercase tracking-widest">
              {index + 1}. {hint.title}
            </p>
            <p className="text-sm leading-relaxed text-zinc-700 dark:text-zinc-300">{hint.text}</p>
            {hint.link && (
              <Link to={hint.link.to} className="text-sm underline">{hint.link.label}</Link>
            )}
          </li>
        ))}
      </ol>
      <button type="button" onClick={dismiss} className={`${primaryButtonClass} mt-6`}>
        Got it
      </button>
    </section>
  );
}
