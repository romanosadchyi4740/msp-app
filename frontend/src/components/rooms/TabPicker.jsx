import { useEffect, useState } from 'react';
import { Check, FileMusic, Guitar, Loader2, Search } from 'lucide-react';
import { useShallow } from 'zustand/react/shallow';
import useTabStore from '../../store/tabStore';
import { inputClass } from '../tabs/tabDetails';

const SEARCH_DELAY_MS = 300;

/**
 * Picks a tab for a lesson: the user's own tabs, or public tabs found by title or artist. Clicking the picked tab
 * again clears the choice.
 */
export default function TabPicker({ selected, onSelect }) {
  const { myTabs, myTabsLoading, fetchMyTabs, search, searchTabs } = useTabStore(useShallow((state) => ({
    myTabs: state.myTabs,
    myTabsLoading: state.myTabsLoading,
    fetchMyTabs: state.fetchMyTabs,
    search: state.search,
    searchTabs: state.searchTabs,
  })));
  const [query, setQuery] = useState('');

  useEffect(() => {
    fetchMyTabs();
  }, [fetchMyTabs]);

  useEffect(() => {
    const trimmed = query.trim();
    if (!trimmed) return undefined;
    const handle = setTimeout(() => searchTabs(trimmed, 0), SEARCH_DELAY_MS);
    return () => clearTimeout(handle);
  }, [query, searchTabs]);

  const searching = query.trim().length > 0;
  // Hidden tabs cannot be used in a lesson
  const tabs = (searching ? (search.query === query.trim() ? search.results : []) : myTabs)
    .filter((tab) => !tab.hidden);
  const loading = searching ? search.loading : myTabsLoading;

  return (
    <div className="space-y-3">
      <label className="relative block">
        <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
        <input
          className={`${inputClass} pl-9`}
          placeholder="Search public tabs by title or artist, or pick one of yours below"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
        />
      </label>
      {loading && tabs.length === 0 ? (
        <Loader2 size={20} className="animate-spin text-zinc-500" />
      ) : tabs.length === 0 ? (
        <p className="text-sm text-zinc-500 dark:text-zinc-400">
          {searching ? 'No public tabs match.' : 'You have no tabs yet; search the public ones.'}
        </p>
      ) : (
        <ul className="max-h-72 space-y-2 overflow-y-auto" aria-label={searching ? 'Public tabs' : 'My tabs'}>
          {tabs.map((tab) => {
            const isSelected = selected?.id === tab.id;
            const Icon = tab.format === 'GUITAR_PRO' ? Guitar : FileMusic;
            return (
              <li key={tab.id}>
                <button
                  type="button"
                  aria-pressed={isSelected}
                  onClick={() => onSelect(isSelected ? null : tab)}
                  className={`flex w-full items-center gap-4 border p-3 text-left transition-colors ${
                    isSelected
                      ? 'border-zinc-900 bg-zinc-100 dark:border-white dark:bg-zinc-800'
                      : 'border-zinc-200 hover:border-zinc-900 dark:border-zinc-800 dark:hover:border-white'
                  }`}
                >
                  <Icon size={18} className="shrink-0 text-zinc-500 dark:text-zinc-400" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-bold">{tab.title}</span>
                    <span className="block truncate text-xs text-zinc-500 dark:text-zinc-400">
                      {tab.artist || 'Unknown artist'}
                    </span>
                  </span>
                  {isSelected && <Check size={18} className="shrink-0" />}
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
