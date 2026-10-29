import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useShallow } from 'zustand/react/shallow';
import { FileMusic, Guitar, Loader2 } from 'lucide-react';
import useTabStore from '../store/tabStore';
import TabCanvas from '../components/tabs/TabCanvas';
import TabDetailsFields from '../components/tabs/TabDetailsFields';
import { EMPTY_DETAILS, inputClass, labelClass, toTabDetails } from '../components/tabs/tabDetails';

const PREVIEW_DELAY_MS = 600;
const GUITAR_PRO_EXTENSIONS = ['.gp', '.gp5', '.gpx'];
const MAX_FILE_BYTES = 10 * 1024 * 1024;

const SAMPLE_TAB = `e|-----0---|-1---3-1-|
B|---1-----|---------|
G|---------|---------|
D|---------|---------|
A|-0-------|---------|
E|---------|---------|`;

/**
 * Creating a tab: write an ASCII tab with a live preview, or upload a Guitar Pro file and preview it before it is
 * stored. Either way the user confirms the material is theirs or in the public domain (R14).
 */
export default function TabNewPage() {
  const navigate = useNavigate();
  const { previewAscii, createAsciiTab, uploadGuitarProTab } = useTabStore(useShallow((state) => ({
    previewAscii: state.previewAscii,
    createAsciiTab: state.createAsciiTab,
    uploadGuitarProTab: state.uploadGuitarProTab,
  })));
  const [mode, setMode] = useState('ascii');
  const [details, setDetails] = useState(EMPTY_DETAILS);
  const [rightsConfirmed, setRightsConfirmed] = useState(false);
  const [rawText, setRawText] = useState('');
  // `text` is the text the preview (or its error) belongs to
  const [preview, setPreview] = useState({ text: null, alphaTex: null, measureCount: 0, error: null });
  const [file, setFile] = useState(null);
  const [fileUrl, setFileUrl] = useState(null);
  const [fileError, setFileError] = useState(null);
  const [saving, setSaving] = useState(false);
  const [progress, setProgress] = useState(0);

  // The ASCII preview follows the text and the details that change the rendered tab, after a short pause
  const { title, artist, tempoBpm, timeSignature } = details;
  useEffect(() => {
    if (mode !== 'ascii' || !rawText.trim()) return undefined;
    let stale = false;
    const handle = setTimeout(async () => {
      try {
        const result = await previewAscii({
          rawText,
          title: title.trim() || null,
          artist: artist.trim() || null,
          tempoBpm: tempoBpm ? Number(tempoBpm) : null,
          timeSignature: timeSignature.trim() || null,
        });
        if (!stale) {
          setPreview({ text: rawText, alphaTex: result.alphaTex, measureCount: result.measureCount, error: null });
        }
      } catch (error) {
        if (!stale) {
          const message = error.response?.data?.message ?? 'The preview could not be made';
          setPreview((current) => ({ ...current, text: rawText, error: message }));
        }
      }
    }, PREVIEW_DELAY_MS);
    return () => {
      stale = true;
      clearTimeout(handle);
    };
  }, [mode, rawText, title, artist, tempoBpm, timeSignature, previewAscii]);

  // The chosen file is previewed from the browser, before anything is uploaded
  useEffect(() => () => {
    if (fileUrl) URL.revokeObjectURL(fileUrl);
  }, [fileUrl]);

  // A new source reloads the canvas, so it only changes when the previewed tab does, not on every keystroke
  const hasText = rawText.trim() !== '';
  const asciiSource = useMemo(
    () => (preview.alphaTex && hasText ? { alphaTex: preview.alphaTex } : null),
    [preview.alphaTex, hasText],
  );
  const previewCurrent = preview.text === rawText;
  const fileSource = useMemo(() => (fileUrl ? { fileUrl } : null), [fileUrl]);
  const source = mode === 'ascii' ? asciiSource : fileSource;

  const handleFile = (event) => {
    const chosen = event.target.files[0] ?? null;
    setFileError(null);
    if (chosen) {
      const name = chosen.name.toLowerCase();
      let problem = null;
      if (!GUITAR_PRO_EXTENSIONS.some((extension) => name.endsWith(extension))) {
        problem = 'Choose a Guitar Pro file (.gp, .gp5 or .gpx).';
      } else if (chosen.size > MAX_FILE_BYTES) {
        problem = 'The file can be at most 10 MB.';
      }
      if (problem) {
        setFileError(problem);
        setFile(null);
        setFileUrl(null);
        return;
      }
      if (!details.title.trim()) {
        setDetails({ ...details, title: chosen.name.replace(/\.[^.]+$/, '') });
      }
    }
    setFile(chosen);
    setFileUrl(chosen ? URL.createObjectURL(chosen) : null);
  };

  // A text tab is saved once its current text has been previewed without an error
  const content = mode === 'ascii' ? hasText && previewCurrent && !preview.error : file;
  const canSave = details.title.trim() && rightsConfirmed && content && !saving;

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!canSave) return;
    setSaving(true);
    setProgress(0);
    const tabDetails = toTabDetails(details);
    const created = mode === 'ascii'
      ? await createAsciiTab({ ...tabDetails, rawText, rightsConfirmed })
      : await uploadGuitarProTab(file, { ...tabDetails, rightsConfirmed }, setProgress);
    setSaving(false);
    if (created) navigate(`/tabs/${created.id}`);
  };

  const modeButton = (value, Icon, label) => (
    <button
      type="button"
      onClick={() => setMode(value)}
      className={`flex items-center gap-2 border px-4 py-2 text-xs font-black uppercase tracking-widest ${
        mode === value
          ? 'border-zinc-900 bg-zinc-900 text-white dark:border-white dark:bg-white dark:text-black'
          : 'border-zinc-300 text-zinc-500 dark:border-zinc-700 dark:text-zinc-400'
      }`}
    >
      <Icon size={14} /> {label}
    </button>
  );

  return (
    <form onSubmit={handleSubmit} className="space-y-8 pb-32">
      <header className="space-y-4 border-b border-zinc-200 pb-6 dark:border-zinc-800">
        <h1 className="text-4xl font-black tracking-tighter">New tab</h1>
        <div className="flex flex-wrap gap-2">
          {modeButton('ascii', FileMusic, 'Write a text tab')}
          {modeButton('file', Guitar, 'Upload a Guitar Pro file')}
        </div>
      </header>

      <div className="grid grid-cols-1 gap-8 xl:grid-cols-[minmax(0,2fr)_minmax(0,3fr)]">
        <div className="space-y-6">
          <TabDetailsFields value={details} onChange={setDetails} />

          {mode === 'ascii' ? (
            <label className="block">
              <span className={labelClass}>Tab text *</span>
              <textarea
                className={`${inputClass} h-72 font-mono text-xs leading-5`}
                placeholder={SAMPLE_TAB}
                value={rawText}
                onChange={(event) => setRawText(event.target.value)}
                spellCheck={false}
              />
              <span className="mt-1 block text-xs text-zinc-500 dark:text-zinc-400">
                One line per string, highest first (e|, B|, G|, D|, A|, E|), with | between measures.
              </span>
            </label>
          ) : (
            <label className="block">
              <span className={labelClass}>Guitar Pro file *</span>
              <input type="file" accept={GUITAR_PRO_EXTENSIONS.join(',')} onChange={handleFile} className="text-sm" />
              <span className="mt-1 block text-xs text-zinc-500 dark:text-zinc-400">.gp, .gp5 or .gpx, at most 10 MB.</span>
              {fileError && <span className="mt-1 block text-xs text-red-500">{fileError}</span>}
            </label>
          )}

          <label className="flex items-start gap-2 text-sm">
            <input
              type="checkbox"
              className="mt-1"
              checked={rightsConfirmed}
              onChange={(event) => setRightsConfirmed(event.target.checked)}
            />
            <span>
              I wrote this material myself, have the right to share it, or it is in the public domain. *
            </span>
          </label>

          <button
            type="submit"
            disabled={!canSave}
            className="flex items-center gap-2 bg-zinc-900 px-6 py-3 text-xs font-black uppercase tracking-widest text-white disabled:opacity-40 dark:bg-white dark:text-black"
          >
            {saving && <Loader2 size={14} className="animate-spin" />}
            {saving && mode === 'file' ? `Uploading ${progress}%` : 'Save tab'}
          </button>
        </div>

        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className={labelClass}>Preview</span>
            {mode === 'ascii' && preview.alphaTex && !preview.error && (
              <span className="text-xs text-zinc-500 dark:text-zinc-400">{preview.measureCount} measures</span>
            )}
          </div>
          {mode === 'ascii' && preview.error && previewCurrent && hasText && (
            <p className="text-xs text-red-500">{preview.error}</p>
          )}
          {source ? (
            <TabCanvas source={source} className="h-[60vh] border border-zinc-200 dark:border-zinc-800" />
          ) : (
            <div className="flex h-[60vh] items-center justify-center border border-dashed border-zinc-300 text-sm text-zinc-500 dark:border-zinc-700 dark:text-zinc-400">
              {mode === 'ascii' ? 'Type or paste a tab to see it here.' : 'Choose a file to see it here.'}
            </div>
          )}
        </div>
      </div>
    </form>
  );
}
