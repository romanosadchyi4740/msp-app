import { useRef } from 'react';
import { Copy } from 'lucide-react';
import toast from 'react-hot-toast';
import { inputClass } from '../tabs/tabDetails';

const COPY_TIMEOUT_MS = 1000;

function inviteUrl(code) {
  return `${window.location.origin}/join/${code}`;
}

/**
 * The room's invite link with one button that copies it.
 */
export default function InviteLink({ code }) {
  const inputRef = useRef(null);
  const url = inviteUrl(code);

  const copy = async () => {
    // Selected first, so the link is ready to copy by hand if the clipboard is not available
    inputRef.current?.select();
    let timer;
    try {
      // The browser may leave the request unanswered (e.g. a pending permission), so it gets a time limit
      await Promise.race([
        navigator.clipboard.writeText(url),
        new Promise((_, reject) => {
          timer = setTimeout(() => reject(new Error('Clipboard timed out')), COPY_TIMEOUT_MS);
        }),
      ]);
      toast.success('Invite link copied');
    } catch {
      toast.error('Could not copy; the link is selected, copy it by hand');
    } finally {
      clearTimeout(timer);
    }
  };

  return (
    <div className="flex gap-2">
      <input
        ref={inputRef}
        readOnly
        aria-label="Invite link"
        className={`${inputClass} font-mono`}
        value={url}
        onFocus={(event) => event.target.select()}
      />
      <button
        type="button"
        onClick={copy}
        className="flex shrink-0 items-center gap-2 bg-zinc-900 px-5 py-2 text-xs font-black uppercase tracking-widest text-white hover:bg-zinc-700 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
      >
        <Copy size={14} /> Copy link
      </button>
    </div>
  );
}
