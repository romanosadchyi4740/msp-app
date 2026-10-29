export const inputClass = 'w-full bg-transparent border border-zinc-300 dark:border-zinc-700 px-3 py-2 text-sm '
  + 'focus:outline-none focus:border-zinc-900 dark:focus:border-white';
export const labelClass = 'block text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 mb-1';

export const EMPTY_DETAILS = { title: '', artist: '', tempoBpm: '', timeSignature: '', isPublic: false };

/**
 * A tab from the API as form values for `TabDetailsFields`.
 */
export function detailsOf(tab) {
  return {
    title: tab.title ?? '',
    artist: tab.artist ?? '',
    tempoBpm: tab.tempoBpm ? String(tab.tempoBpm) : '',
    timeSignature: tab.timeSignature ?? '',
    isPublic: !!tab.isPublic,
  };
}

/**
 * The form values as the API expects them: blank optional fields become null, the tempo a number.
 */
export function toTabDetails(value) {
  const blankToNull = (text) => (text && text.trim() ? text.trim() : null);
  return {
    title: value.title.trim(),
    artist: blankToNull(value.artist),
    tempoBpm: value.tempoBpm ? Number(value.tempoBpm) : null,
    timeSignature: blankToNull(value.timeSignature),
    isPublic: value.isPublic,
  };
}
