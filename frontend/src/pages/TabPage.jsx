import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { useShallow } from 'zustand/react/shallow';
import { ArrowLeft, Loader2, Pencil, Trash2 } from 'lucide-react';
import useTabStore from '../store/tabStore';
import useAuthStore from '../store/authStore';
import TabCanvas from '../components/tabs/TabCanvas';
import TabDetailsFields from '../components/tabs/TabDetailsFields';
import ConfirmDialog from '../components/modals/ConfirmDialog';
import { detailsOf, inputClass, labelClass, toTabDetails } from '../components/tabs/tabDetails';

const badgeClass = 'px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border border-zinc-300 dark:border-zinc-700';

/**
 * One tab on the canvas with its details. Its owner can change the details (and the text of a text tab) or delete it.
 * Keyed by the tab id, so opening another tab starts from a fresh state.
 */
export default function TabPage() {
  const { id } = useParams();
  return <TabView key={id} id={id} />;
}

function TabView({ id }) {
  const navigate = useNavigate();
  const user = useAuthStore((state) => state.user);
  const { fetchTab, fetchFileUrl, updateTab, deleteTab } = useTabStore(useShallow((state) => ({
    fetchTab: state.fetchTab,
    fetchFileUrl: state.fetchFileUrl,
    updateTab: state.updateTab,
    deleteTab: state.deleteTab,
  })));
  const [tab, setTab] = useState(null);
  const [source, setSource] = useState(null);
  // 'missing' for a tab that does not exist or is not readable, 'failed' for any other error
  const [loadError, setLoadError] = useState(null);
  const [measureCount, setMeasureCount] = useState(null);
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(null);
  const [draftText, setDraftText] = useState('');
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  // Incremented to load the tab again after it was changed
  const [version, setVersion] = useState(0);

  useEffect(() => {
    let stale = false;
    const read = async () => {
      const loaded = await fetchTab(id);
      const loadedSource = loaded.format === 'ASCII'
        ? { alphaTex: loaded.alphaTex }
        : { fileUrl: await fetchFileUrl(id) };
      return { loaded, loadedSource };
    };
    read()
      .then(({ loaded, loadedSource }) => {
        if (stale) return;
        setTab(loaded);
        setSource(loadedSource);
      })
      .catch((error) => {
        if (!stale) setLoadError(error.response?.status === 404 ? 'missing' : 'failed');
      });
    return () => {
      stale = true;
    };
  }, [id, version, fetchTab, fetchFileUrl]);

  const isOwner = tab && user?.userId === tab.ownerId;
  const handleLoaded = useCallback(({ measureCount: count }) => setMeasureCount(count), []);
  const facts = useMemo(() => {
    if (!tab) return [];
    return [
      tab.format === 'GUITAR_PRO' ? `Guitar Pro${tab.gpVersion ? ` ${tab.gpVersion}` : ''}` : 'Text tab',
      tab.tempoBpm ? `${tab.tempoBpm} bpm` : null,
      tab.timeSignature,
      measureCount !== null ? `${measureCount} measures` : null,
    ].filter(Boolean);
  }, [tab, measureCount]);

  const startEditing = () => {
    setDraft(detailsOf(tab));
    setDraftText(tab.rawText ?? '');
    setEditing(true);
  };

  const handleSave = async (event) => {
    event.preventDefault();
    setSaving(true);
    const changes = { ...toTabDetails(draft), rawText: tab.format === 'ASCII' ? draftText : null };
    const updated = await updateTab(tab.id, changes);
    setSaving(false);
    if (updated) {
      setEditing(false);
      if (tab.format === 'GUITAR_PRO') {
        // The file cannot change here, so the loaded canvas stays
        setTab(updated);
      } else {
        setVersion((current) => current + 1);
      }
    }
  };

  const handleDelete = async () => {
    setConfirmDelete(false);
    if (await deleteTab(tab.id)) navigate('/tabs');
  };

  if (loadError) {
    return (
      <div className="space-y-4">
        <p className="text-sm">
          {loadError === 'missing'
            ? 'This tab does not exist or is not shared with you.'
            : 'The tab could not be loaded. Try again later.'}
        </p>
        <Link to="/tabs" className="text-sm underline">Back to tabs</Link>
      </div>
    );
  }
  if (!tab) {
    return <Loader2 size={24} className="animate-spin text-zinc-500" />;
  }

  return (
    <div className="space-y-6 pb-32">
      <Link to="/tabs" className="flex items-center gap-2 text-xs font-bold uppercase tracking-widest text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white">
        <ArrowLeft size={14} /> Tabs
      </Link>

      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-zinc-200 pb-6 dark:border-zinc-800">
        <div className="min-w-0 space-y-2">
          <h1 className="truncate text-4xl font-black tracking-tighter">{tab.title}</h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            {[tab.artist, tab.ownerName ? `added by ${tab.ownerName}` : null].filter(Boolean).join(' · ')}
          </p>
          <div className="flex flex-wrap items-center gap-2">
            {facts.map((fact) => <span key={fact} className={badgeClass}>{fact}</span>)}
            {isOwner && <span className={badgeClass}>{tab.isPublic ? 'Public' : 'Private'}</span>}
            {tab.hidden && (
              <span className={`${badgeClass} border-red-500 text-red-500`}>Hidden after a complaint</span>
            )}
          </div>
        </div>
        {isOwner && !editing && (
          <div className="flex gap-2">
            <button type="button" onClick={startEditing} className="flex items-center gap-2 border border-current px-4 py-2 text-xs font-bold uppercase tracking-widest">
              <Pencil size={14} /> Edit
            </button>
            <button type="button" onClick={() => setConfirmDelete(true)} className="flex items-center gap-2 border border-red-500 px-4 py-2 text-xs font-bold uppercase tracking-widest text-red-500">
              <Trash2 size={14} /> Delete
            </button>
          </div>
        )}
      </header>

      {editing && (
        <form onSubmit={handleSave} className="space-y-4 border border-zinc-200 p-6 dark:border-zinc-800">
          <TabDetailsFields value={draft} onChange={setDraft} />
          {tab.format === 'ASCII' && (
            <label className="block">
              <span className={labelClass}>Tab text</span>
              <textarea
                className={`${inputClass} h-56 font-mono text-xs leading-5`}
                value={draftText}
                onChange={(event) => setDraftText(event.target.value)}
                spellCheck={false}
              />
            </label>
          )}
          <div className="flex gap-2">
            <button
              type="submit"
              disabled={saving || !draft.title.trim()}
              className="flex items-center gap-2 bg-zinc-900 px-5 py-2 text-xs font-black uppercase tracking-widest text-white disabled:opacity-40 dark:bg-white dark:text-black"
            >
              {saving && <Loader2 size={14} className="animate-spin" />} Save
            </button>
            <button type="button" onClick={() => setEditing(false)} className="px-5 py-2 text-xs font-bold uppercase tracking-widest text-zinc-500">
              Cancel
            </button>
          </div>
        </form>
      )}

      <TabCanvas source={source} onLoaded={handleLoaded} className="h-[70vh] border border-zinc-200 dark:border-zinc-800" />

      <ConfirmDialog
        isOpen={confirmDelete}
        title="Delete this tab?"
        message={`"${tab.title}" will be deleted for good.`}
        confirmText="Delete"
        onConfirm={handleDelete}
        onCancel={() => setConfirmDelete(false)}
      />
    </div>
  );
}
