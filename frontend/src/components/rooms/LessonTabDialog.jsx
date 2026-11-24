import { useState } from 'react';
import { Loader2, X } from 'lucide-react';
import TabPicker from './TabPicker';
import { primaryButtonClass, secondaryButtonClass } from './roomStyles';

/**
 * The host picks the tab to start the lesson with or to put on screen instead (KAN-50). `onConfirm(tab)` returns
 * whether it worked; the dialog stays open on failure, whose message the request already showed.
 */
export default function LessonTabDialog({ isOpen, title, confirmText, note, initialTab = null, onConfirm, onCancel }) {
  if (!isOpen) return null;
  return (
    <TabDialog
      title={title}
      confirmText={confirmText}
      note={note}
      initialTab={initialTab}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />
  );
}

// Mounted only while open, so every opening starts from the initial tab
function TabDialog({ title, confirmText, note, initialTab, onConfirm, onCancel }) {
  const [tab, setTab] = useState(initialTab);
  const [busy, setBusy] = useState(false);

  const confirm = async () => {
    setBusy(true);
    const done = await onConfirm(tab);
    if (!done) setBusy(false);
  };

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-label={title}>
      <div className="absolute inset-0 bg-white/60 backdrop-blur-sm dark:bg-black/60" onClick={busy ? undefined : onCancel} />
      <div className="relative w-full max-w-lg space-y-6 border border-zinc-200 bg-white p-8 shadow-2xl dark:border-zinc-800 dark:bg-zinc-900">
        <div className="flex items-start justify-between gap-4">
          <h2 className="text-sm font-black uppercase tracking-widest">{title}</h2>
          <button type="button" onClick={onCancel} disabled={busy} aria-label="Close" className="text-zinc-600 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white">
            <X size={18} />
          </button>
        </div>
        <TabPicker selected={tab} onSelect={setTab} />
        {note && <p className="text-xs text-zinc-600 dark:text-zinc-400">{note}</p>}
        <div className="flex justify-end gap-3">
          <button type="button" onClick={onCancel} disabled={busy} className={secondaryButtonClass}>Cancel</button>
          <button type="button" onClick={confirm} disabled={!tab || busy} className={primaryButtonClass}>
            {busy && <Loader2 size={14} className="animate-spin" />} {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
}
