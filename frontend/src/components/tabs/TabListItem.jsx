import { Link } from 'react-router-dom';
import { FileMusic, Guitar } from 'lucide-react';

const badgeClass = 'px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border border-zinc-300 dark:border-zinc-700';

/**
 * One tab in a list, linking to its page. `showOwner` adds who uploaded it (for search results).
 */
export default function TabListItem({ tab, showOwner = false }) {
  const Icon = tab.format === 'GUITAR_PRO' ? Guitar : FileMusic;
  return (
    <Link
      to={`/tabs/${tab.id}`}
      className="flex items-center gap-4 border border-zinc-200 p-4 transition-colors hover:border-zinc-900 dark:border-zinc-800 dark:hover:border-white"
    >
      <Icon size={20} className="shrink-0 text-zinc-500 dark:text-zinc-400" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-bold">{tab.title}</p>
        <p className="truncate text-xs text-zinc-500 dark:text-zinc-400">
          {[tab.artist, showOwner && tab.ownerName ? `added by ${tab.ownerName}` : null].filter(Boolean).join(' · ')
            || 'Unknown artist'}
        </p>
      </div>
      <div className="flex shrink-0 flex-wrap items-center gap-2">
        <span className={badgeClass}>{tab.format === 'GUITAR_PRO' ? `Guitar Pro${tab.gpVersion ? ` ${tab.gpVersion}` : ''}` : 'Text'}</span>
        {!showOwner && <span className={badgeClass}>{tab.isPublic ? 'Public' : 'Private'}</span>}
        {tab.hidden && <span className={`${badgeClass} border-red-500 text-red-500`}>Hidden</span>}
      </div>
    </Link>
  );
}
