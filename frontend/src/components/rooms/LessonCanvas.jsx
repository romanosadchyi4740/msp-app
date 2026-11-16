import { useCallback, useEffect, useRef, useState } from 'react';
import { Loader2 } from 'lucide-react';
import TabCanvas from '../tabs/TabCanvas';
import useTabStore from '../../store/tabStore';
import usePlaybackStore from '../../store/playbackStore';
import { onPlaybackFrame } from '../../sync/syncEngine';
import { errorMessage } from './roomStyles';

const placeholderClass = 'flex h-[60vh] items-center justify-center border border-dashed border-zinc-300 p-6 '
  + 'text-center text-sm text-zinc-600 dark:border-zinc-700 dark:text-zinc-400';

/**
 * The tab of the room's active lesson (`lesson`, from useCurrentLesson), scrolled to the shared position by the sync
 * engine, which the room page runs. Follows the lesson changing its tab without a reload; `placeholder` is shown
 * while the room has no lesson.
 */
export default function LessonCanvas({ lesson, placeholder }) {
  const fetchTab = useTabStore((state) => state.fetchTab);
  const fetchFileUrl = useTabStore((state) => state.fetchFileUrl);
  const canvasRef = useRef(null);
  // The tab content of the tab id it was loaded for
  const [tab, setTab] = useState({ tabId: null, source: null, error: null });
  const tabId = lesson?.tabId ?? null;

  useEffect(() => {
    if (!tabId) return undefined;
    let current = true;
    const read = async () => {
      const loaded = await fetchTab(tabId);
      return loaded.format === 'ASCII' ? { alphaTex: loaded.alphaTex } : { fileUrl: await fetchFileUrl(tabId) };
    };
    read()
      .then((source) => current && setTab({ tabId, source, error: null }))
      .catch((failure) => current && setTab({
        tabId,
        source: null,
        error: failure.response?.status === 404
          ? 'The lesson\'s tab is not available to you.'
          : errorMessage(failure, 'Could not load the lesson\'s tab.'),
      }));
    return () => {
      current = false;
    };
  }, [tabId, fetchTab, fetchFileUrl]);

  useEffect(() => onPlaybackFrame(({ measure, beat }) => canvasRef.current?.setPosition(measure, beat)), []);

  // The measures of the tab shown, for wrapping positions (KAN-95); forgotten when another tab or none is shown
  const setMeasureSignatures = usePlaybackStore((state) => state.setMeasureSignatures);
  const handleLoaded = useCallback(({ timeSignatures }) => setMeasureSignatures(timeSignatures), [setMeasureSignatures]);
  const shownTab = tabId && tab.tabId === tabId ? tab : null;
  const shownSource = shownTab?.source ?? null;
  useEffect(() => () => setMeasureSignatures(null), [shownSource, setMeasureSignatures]);

  if (!lesson) {
    return <div className={placeholderClass}>{placeholder}</div>;
  }
  if (!tabId) {
    return <div className={placeholderClass}>No tab is open in this lesson.</div>;
  }
  if (shownTab?.error) {
    return <div className={placeholderClass}>{shownTab.error}</div>;
  }
  if (!shownSource) {
    return <div className={placeholderClass}><Loader2 size={20} className="animate-spin" /></div>;
  }
  return (
    <TabCanvas
      ref={canvasRef}
      source={shownSource}
      onLoaded={handleLoaded}
      className="h-[60vh] border border-zinc-200 lg:h-[calc(100vh-16rem)] dark:border-zinc-800"
    />
  );
}
