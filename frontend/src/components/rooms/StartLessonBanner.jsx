import { Link } from 'react-router-dom';
import { Guitar, Plus } from 'lucide-react';
import { primaryButtonClass, secondaryButtonClass } from './roomStyles';

/**
 * The home page's way into SyncChord (KAN-53): room creation is one click away.
 */
export default function StartLessonBanner() {
  return (
    <section
      aria-labelledby="start-lesson-title"
      className="flex flex-wrap items-center justify-between gap-6 border border-zinc-200 p-8 dark:border-zinc-800"
    >
      <div className="max-w-xl space-y-2">
        <p className="flex items-center gap-2 text-xs font-black uppercase tracking-[0.2em] text-zinc-600 dark:text-zinc-400">
          <Guitar size={14} /> SyncChord
        </p>
        <h2 id="start-lesson-title" className="text-2xl font-black tracking-tighter">
          Teach or rehearse guitar live
        </h2>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">
          Open a room, pick a tab and send the invite link: everyone sees the same tab scroll in sync and hears one
          metronome.
        </p>
      </div>
      <div className="flex flex-wrap gap-3">
        <Link to="/rooms/new" className={primaryButtonClass}>
          <Plus size={14} /> Start a lesson
        </Link>
        <Link to="/rooms" className={secondaryButtonClass}>
          My rooms
        </Link>
      </div>
    </section>
  );
}
