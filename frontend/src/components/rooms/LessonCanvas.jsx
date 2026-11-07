import { useEffect, useRef, useState } from 'react';
import { Loader2 } from 'lucide-react';
import TabCanvas from '../tabs/TabCanvas';
import useRoomStore from '../../store/roomStore';
import useTabStore from '../../store/tabStore';
import usePlaybackStore from '../../store/playbackStore';
import { onPlaybackFrame } from '../../sync/syncEngine';
import { errorMessage, sectionTitleClass } from './roomStyles';

const STATUS_LABELS = { PLAYING: 'Playing', PAUSED: 'Paused', STOPPED: 'Stopped' };

/**
 * The tab of the room's active lesson, scrolled to the shared position by the sync engine, which the room page runs.
 * Read-only: the host's controls come with the full room page. Shows nothing while the room has no lesson; follows
 * lessons starting, ending and changing their tab without a reload.
 */
export default function LessonCanvas({ roomId }) {
  const fetchCurrentLesson = useRoomStore((state) => state.fetchCurrentLesson);
  const fetchTab = useTabStore((state) => state.fetchTab);
  const fetchFileUrl = useTabStore((state) => state.fetchFileUrl);
  const lessonEvent = usePlaybackStore((state) => state.lessonEvent);
  const snapshotCount = usePlaybackStore((state) => state.snapshotCount);
  const status = usePlaybackStore((state) => state.playback?.status ?? null);
  const canvasRef = useRef(null);
  // The lesson of the room it was loaded for, so another room never shows this one's lesson
  const [lesson, setLesson] = useState({ roomId: null, value: null, error: null });
  // The tab content of the tab id it was loaded for
  const [tab, setTab] = useState({ tabId: null, source: null, error: null });

  // The lesson changes with every lesson event; the event itself carries too little to show it. Events sent while the
  // connection was down are not replayed, so every snapshot (each reconnect) loads it again too
  useEffect(() => {
    let current = true;
    fetchCurrentLesson(roomId)
      .then((value) => current && setLesson({ roomId, value, error: null }))
      .catch((failure) => current && setLesson({
        roomId,
        value: null,
        error: errorMessage(failure, 'Could not load the lesson.'),
      }));
    return () => {
      current = false;
    };
  }, [roomId, lessonEvent, snapshotCount, fetchCurrentLesson]);

  const activeLesson = lesson.roomId === roomId ? lesson.value : null;
  const tabId = activeLesson?.tabId ?? null;

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

  const lessonError = lesson.roomId === roomId ? lesson.error : null;
  if (lessonError) {
    return <p className="text-sm text-zinc-500 dark:text-zinc-400">{lessonError}</p>;
  }
  if (!activeLesson) {
    return null;
  }
  const shownTab = tab.tabId === tabId ? tab : null;

  return (
    <section className="space-y-3">
      <div className="flex items-baseline justify-between gap-4">
        <h2 className={sectionTitleClass}>Lesson</h2>
        {status && <span className="text-xs text-zinc-500 dark:text-zinc-400">{STATUS_LABELS[status] ?? status}</span>}
      </div>
      {!tabId && <p className="text-sm text-zinc-500 dark:text-zinc-400">No tab is open in this lesson.</p>}
      {tabId && shownTab?.error && <p className="text-sm text-zinc-500 dark:text-zinc-400">{shownTab.error}</p>}
      {tabId && !shownTab && <Loader2 size={20} className="animate-spin text-zinc-500" />}
      {shownTab?.source && (
        <TabCanvas
          ref={canvasRef}
          source={shownTab.source}
          className="h-[60vh] border border-zinc-200 dark:border-zinc-800"
        />
      )}
    </section>
  );
}
