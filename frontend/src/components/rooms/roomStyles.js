export const primaryButtonClass = 'flex items-center justify-center gap-2 bg-zinc-900 px-5 py-3 text-xs font-black '
  + 'uppercase tracking-widest text-white hover:bg-zinc-700 disabled:opacity-40 dark:bg-white dark:text-black '
  + 'dark:hover:bg-zinc-200';

export const secondaryButtonClass = 'flex items-center justify-center gap-2 border border-zinc-300 px-5 py-3 text-xs '
  + 'font-black uppercase tracking-widest hover:border-zinc-900 disabled:opacity-40 dark:border-zinc-700 '
  + 'dark:hover:border-white';

export const badgeClass = 'px-2 py-0.5 text-[9px] font-black uppercase tracking-widest border border-zinc-300 '
  + 'dark:border-zinc-700';

export const sectionTitleClass = 'text-xs font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400';

/**
 * The message of a failed request, from the backend's error body when it has one.
 */
export function errorMessage(error, fallback) {
  return error?.response?.data?.message || fallback;
}
