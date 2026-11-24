import { useCallback, useEffect, useRef, useState } from 'react';
import toast from 'react-hot-toast';
import useRoomStore from '../../store/roomStore';
import usePlaybackStore from '../../store/playbackStore';
import { errorMessage } from './roomStyles';

const END_MESSAGES = {
  LIMIT_REACHED: 'The free plan\'s 40 minutes are up, so the lesson ended.',
  HOST_ENDED: 'The host ended the lesson.',
};

/**
 * The room's active lesson (`LessonResponse`, or null while it has none) for the room page, which the sync engine
 * runs. The lesson changes with every lesson event, and the event itself carries too little to show it; events sent
 * while the connection was down are not replayed, so every snapshot (each reconnect) loads it again too. Says when
 * the lesson time is nearly up and when the lesson ended.
 *
 * Returns `{ lesson, error, loaded, reload }`; `loaded` is false until the first answer for this room, and
 * `reload(lesson)` shows the given lesson (or null) at once and reads it again.
 */
export default function useCurrentLesson(roomId, { isHost = false } = {}) {
  const fetchCurrentLesson = useRoomStore((state) => state.fetchCurrentLesson);
  const lessonEvent = usePlaybackStore((state) => state.lessonEvent);
  const snapshotCount = usePlaybackStore((state) => state.snapshotCount);
  // The lesson of the room it was loaded for, so another room never shows this one's lesson
  const [loaded, setLoaded] = useState({ roomId: null, value: null, error: null });
  const [reloads, setReloads] = useState(0);

  useEffect(() => {
    if (!roomId) return undefined;
    let current = true;
    fetchCurrentLesson(roomId)
      .then((value) => current && setLoaded({ roomId, value, error: null }))
      // A failed read (e.g. while reconnecting) does not take away a lesson that still runs; the next event or
      // snapshot reads it again
      .catch((failure) => current && setLoaded((before) => ({
        roomId,
        value: before.roomId === roomId ? before.value : null,
        error: errorMessage(failure, 'Could not load the lesson.'),
      })));
    return () => {
      current = false;
    };
  }, [roomId, lessonEvent, snapshotCount, reloads, fetchCurrentLesson]);

  // Each event is told once, even when the host changes meanwhile
  const toldEvent = useRef(null);
  useEffect(() => {
    if (!lessonEvent || toldEvent.current === lessonEvent) return;
    toldEvent.current = lessonEvent;
    if (lessonEvent.type === 'LIMIT_WARNING') {
      toast('5 minutes of the lesson left on the free plan.', { icon: '⏱' });
    } else if (lessonEvent.type === 'LESSON_ENDED') {
      // The host who ended it knows; closing the room ends it too, which the room page shows itself
      const message = lessonEvent.reason === 'HOST_ENDED' && isHost ? null : END_MESSAGES[lessonEvent.reason];
      if (message) toast(message);
    }
  }, [lessonEvent, isHost]);

  // The host's own change shows at once with the lesson the request answered (null once ended), then it is read again
  const reload = useCallback((value) => {
    if (value !== undefined) setLoaded({ roomId, value, error: null });
    setReloads((count) => count + 1);
  }, [roomId]);
  const mine = loaded.roomId === roomId;
  return {
    lesson: mine ? loaded.value : null,
    // Shown only while there is no lesson to show
    error: mine && !loaded.value ? loaded.error : null,
    loaded: mine,
    reload,
  };
}
