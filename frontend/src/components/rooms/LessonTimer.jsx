import { useEffect, useState } from 'react';
import { Timer } from 'lucide-react';
import { serverNow } from '../../sync/clockSync';
import { timeLeft } from '../../sync/lessonControls';

/** Below this many milliseconds left the timer stands out. */
const WARNING_MS = 5 * 60 * 1000;

/**
 * The lesson time left on the free plan's 40-minute limit, on the synced server clock. It only shows the time: the
 * server ends the lesson when it is up. A lesson without a limit (Premium) says so.
 */
export default function LessonTimer({ endsAt }) {
  const [now, setNow] = useState(() => serverNow());

  useEffect(() => {
    if (!endsAt) return undefined;
    // Only a display refreshed once a second; nothing is timed by it
    const handle = setInterval(() => setNow(serverNow()), 1000);
    return () => clearInterval(handle);
  }, [endsAt]);

  if (!endsAt) {
    return <p className="flex items-center gap-2 text-xs text-zinc-500 dark:text-zinc-400"><Timer size={14} /> No time limit</p>;
  }
  const warning = Date.parse(endsAt) - now <= WARNING_MS;
  return (
    <p
      className={`flex items-center gap-2 text-sm font-bold tabular-nums ${warning ? 'text-red-600 dark:text-red-400' : ''}`}
      title="The free plan ends a lesson after 40 minutes"
      aria-label="Lesson time left"
    >
      <Timer size={16} /> {timeLeft(endsAt, now)} left
    </p>
  );
}
