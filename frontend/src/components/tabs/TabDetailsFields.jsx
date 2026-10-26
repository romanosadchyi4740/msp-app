import { inputClass, labelClass } from './tabDetails';

/**
 * The details every tab has: title, artist, tempo, time signature and whether it is public.
 * `value` holds them as strings (tempo too) and a boolean `isPublic`; `onChange` receives the whole new value.
 */
export default function TabDetailsFields({ value, onChange }) {
  const set = (field) => (event) => {
    const next = event.target.type === 'checkbox' ? event.target.checked : event.target.value;
    onChange({ ...value, [field]: next });
  };

  return (
    <div className="grid grid-cols-1 gap-4 md:grid-cols-2">
      <label className="md:col-span-2">
        <span className={labelClass}>Title *</span>
        <input className={inputClass} value={value.title} onChange={set('title')} maxLength={255} required />
      </label>
      <label className="md:col-span-2">
        <span className={labelClass}>Artist</span>
        <input className={inputClass} value={value.artist} onChange={set('artist')} maxLength={255} />
      </label>
      <label>
        <span className={labelClass}>Tempo (bpm)</span>
        <input className={inputClass} type="number" min="1" max="400" value={value.tempoBpm} onChange={set('tempoBpm')} />
      </label>
      <label>
        <span className={labelClass}>Time signature</span>
        <input className={inputClass} placeholder="4/4" value={value.timeSignature} onChange={set('timeSignature')} />
      </label>
      <label className="flex items-center gap-2 text-sm md:col-span-2">
        <input type="checkbox" checked={value.isPublic} onChange={set('isPublic')} />
        Public: other users can find this tab in search
      </label>
    </div>
  );
}
