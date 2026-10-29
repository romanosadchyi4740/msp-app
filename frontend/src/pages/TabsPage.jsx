import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Loader2, Plus, Search } from 'lucide-react';
import { useShallow } from 'zustand/react/shallow';
import useTabStore from '../store/tabStore';
import TabListItem from '../components/tabs/TabListItem';
import { inputClass } from '../components/tabs/tabDetails';

const SEARCH_DELAY_MS = 300;

/**
 * The tab library: the user's own tabs and a search over public tabs by title and artist.
 */
export default function TabsPage() {
  const { myTabs, myTabsLoading, fetchMyTabs, search, searchTabs } = useTabStore(useShallow((state) => ({
    myTabs: state.myTabs,
    myTabsLoading: state.myTabsLoading,
    fetchMyTabs: state.fetchMyTabs,
    search: state.search,
    searchTabs: state.searchTabs,
  })));
  const [query, setQuery] = useState(search.query);

  useEffect(() => {
    fetchMyTabs();
  }, [fetchMyTabs]);

  useEffect(() => {
    const handle = setTimeout(() => searchTabs(query.trim(), 0), SEARCH_DELAY_MS);
    return () => clearTimeout(handle);
  }, [query, searchTabs]);

  return (
    <div className="space-y-12 pb-32">
      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-zinc-200 pb-8 dark:border-zinc-800">
        <div>
          <h1 className="text-5xl font-black tracking-tighter">Tabs</h1>
          <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
            Your tabs for lessons and rehearsals, and tabs shared by others.
          </p>
        </div>
        <Link
          to="/tabs/new"
          className="flex items-center gap-2 bg-zinc-900 px-5 py-3 text-xs font-black uppercase tracking-widest text-white hover:bg-zinc-700 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
        >
          <Plus size={16} /> New tab
        </Link>
      </header>

      <section className="space-y-4">
        <h2 className="text-xs font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400">My tabs</h2>
        {myTabsLoading && myTabs.length === 0 ? (
          <Loader2 size={20} className="animate-spin text-zinc-500" />
        ) : myTabs.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            You have no tabs yet. <Link to="/tabs/new" className="underline">Create your first tab.</Link>
          </p>
        ) : (
          <div className="space-y-2">
            {myTabs.map((tab) => <TabListItem key={tab.id} tab={tab} />)}
          </div>
        )}
      </section>

      <section className="space-y-4">
        <h2 className="text-xs font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400">Find tabs</h2>
        <label className="relative block">
          <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500" />
          <input
            className={`${inputClass} pl-9`}
            placeholder="Search public tabs by title or artist"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
          />
        </label>
        {search.loading && search.results.length === 0 ? (
          <Loader2 size={20} className="animate-spin text-zinc-500" />
        ) : search.results.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">No public tabs match.</p>
        ) : (
          <>
            <p className="text-xs text-zinc-500 dark:text-zinc-400">
              {search.totalElements} {search.totalElements === 1 ? 'tab' : 'tabs'}
            </p>
            <div className="space-y-2">
              {search.results.map((tab) => <TabListItem key={tab.id} tab={tab} showOwner />)}
            </div>
            {search.totalPages > 1 && (
              <div className="flex items-center gap-4 text-xs">
                <button
                  type="button"
                  className="border border-current px-3 py-1 disabled:opacity-30"
                  disabled={search.pageNo === 0}
                  onClick={() => searchTabs(search.query, search.pageNo - 1)}
                >
                  Previous
                </button>
                <span>Page {search.pageNo + 1} of {search.totalPages}</span>
                <button
                  type="button"
                  className="border border-current px-3 py-1 disabled:opacity-30"
                  disabled={search.pageNo + 1 >= search.totalPages}
                  onClick={() => searchTabs(search.query, search.pageNo + 1)}
                >
                  Next
                </button>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
