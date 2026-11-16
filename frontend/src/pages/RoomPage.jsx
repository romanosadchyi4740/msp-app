import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Loader2, LogOut, Play } from 'lucide-react';
import useRoomStore, { plannedTabOf } from '../store/roomStore';
import useAuthStore from '../store/authStore';
import InviteLink from '../components/rooms/InviteLink';
import ConfirmDialog from '../components/modals/ConfirmDialog';
import {
  badgeClass, errorMessage, primaryButtonClass, secondaryButtonClass, sectionTitleClass,
} from '../components/rooms/roomStyles';
import LessonCanvas from '../components/rooms/LessonCanvas';
import LessonPanel from '../components/rooms/LessonPanel';
import LessonTabDialog from '../components/rooms/LessonTabDialog';
import ParticipantList from '../components/rooms/ParticipantList';
import RoomChat from '../components/rooms/RoomChat';
import useCurrentLesson from '../components/rooms/useCurrentLesson';
import { startSyncEngine } from '../sync/syncEngine';
import { startMetronome } from '../sync/metronome';
import { installSyncDebugHook } from '../sync/syncDebug';
import SyncDebugOverlay from '../components/rooms/SyncDebugOverlay';
import AudioPanel from '../components/rooms/AudioPanel';
import RoomHints from '../components/rooms/RoomHints';
import socketClient from '../api/socketClient';
import { setRoomParticipants, startRtcMesh } from '../rtc/rtcMesh';
import { installRtcDebugHook } from '../rtc/rtcDebug';
import { startQualityMonitor } from '../rtc/qualityMonitor';

const FREE_LIMIT_NOTE = 'On the free plan a lesson lasts up to 40 minutes, counted from its start.';

/**
 * A room the user is in (KAN-50): the lesson's tab in the middle, following the shared position (KAN-42); beside it
 * the lesson controls (the host's, read-only for the others) with the time left, the invite link, the audio
 * (KAN-47) and who is in the room with their connection quality (KAN-48). The host starts the lesson, changes its
 * tab and ends it here; everything follows without a reload.
 *
 * Mounted afresh for every room id, so nothing of one room (an open dialog, a pending start) carries over to the next.
 */
export default function RoomPageRoute() {
  const { id } = useParams();
  return <RoomPage key={id} id={id} />;
}

function RoomPage({ id }) {
  // ?debug=1 shows the sync figures and exposes the hook the drift test reads (KAN-44)
  const [searchParams] = useSearchParams();
  const debug = searchParams.get('debug') === '1';
  const navigate = useNavigate();
  const fetchRoom = useRoomStore((state) => state.fetchRoom);
  const leaveRoom = useRoomStore((state) => state.leaveRoom);
  const startLesson = useRoomStore((state) => state.startLesson);
  const changeLessonTab = useRoomStore((state) => state.changeLessonTab);
  const endLesson = useRoomStore((state) => state.endLesson);
  const plannedTab = useRoomStore((state) => plannedTabOf(state, id));
  const userId = useAuthStore((state) => state.user?.userId);
  // The room of the id it was loaded for, so another room shows the spinner until its own data arrives
  const [loaded, setLoaded] = useState({ id: null, room: null, error: null });
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [leaving, setLeaving] = useState(false);
  // Which dialog is open: 'start' (pick a tab and start), 'change' (another tab), 'end' (confirm ending)
  const [dialog, setDialog] = useState(null);
  const [starting, setStarting] = useState(false);

  useEffect(() => {
    let current = true;
    fetchRoom(id)
      .then((room) => current && setLoaded({ id, room, error: null }))
      .catch((failure) => current && setLoaded({
        id,
        room: null,
        error: failure.response?.status === 404
          ? 'There is no such room, or you are not in it.'
          : errorMessage(failure, 'Could not open the room.'),
      }));
    return () => {
      current = false;
    };
  }, [id, fetchRoom]);

  const room = loaded.id === id ? loaded.room : null;
  const error = loaded.id === id ? loaded.error : null;
  // The room's id as the server spells it, once it confirmed the user is in the room: the STOMP guard closes the
  // connection on a room topic of anyone else, or of an id in another spelling
  const roomId = room?.id;
  const isHost = room?.myRole === 'HOST';
  const { lesson, error: lessonError, loaded: lessonLoaded, reload: reloadLesson } = useCurrentLesson(roomId, { isHost });

  // The shared lesson playback and its metronome, on the synced server clock, while the room is open
  useEffect(() => {
    if (!roomId) return undefined;
    const stopEngine = startSyncEngine(roomId);
    const stopMetronome = startMetronome();
    return () => {
      stopMetronome();
      stopEngine();
    };
  }, [roomId]);

  useEffect(() => (debug && roomId ? installSyncDebugHook(roomId) : undefined), [debug, roomId]);

  // The audio connections to the other participants. Started before the room is watched below, so this client
  // listens for calls before the others learn that it has the room open
  useEffect(() => {
    if (!roomId || !userId) return undefined;
    const stopMesh = startRtcMesh(roomId, userId);
    const stopMonitor = startQualityMonitor(roomId);
    return () => {
      stopMonitor();
      stopMesh();
    };
  }, [roomId, userId]);

  useEffect(() => (debug && roomId ? installRtcDebugHook() : undefined), [debug, roomId]);

  // Who is in the room and has it open, live. Subscribing is what makes this user online for the others; every
  // change, and every (re)connect, reads the room again, so nothing depends on having seen each event
  useEffect(() => {
    if (!roomId) return undefined;
    let current = true;
    let subscription = null;
    let requests = 0;
    const refresh = () => {
      // Events can come faster than answers; only the answer to the latest request counts
      requests += 1;
      const request = requests;
      const latest = () => current && request === requests;
      return fetchRoom(roomId)
        .then((fresh) => latest()
          && setLoaded((before) => (before.id === id ? { id, room: fresh, error: null } : before)))
        .catch((failure) => {
          // Not in the room any more, e.g. it was left in another browser tab
          if (latest() && failure.response?.status === 404) {
            setLoaded({ id, room: null, error: 'There is no such room, or you are not in it.' });
          }
        });
    };
    const stopListening = socketClient.onConnect(() => {
      subscription = socketClient.subscribe(`/topic/rooms/${roomId}/participants`, (event) => {
        if (event.type !== 'RTT') {
          refresh();
          return;
        }
        // A reported round trip comes with the event, every 10 seconds from everyone: no need to read the room
        setLoaded((before) => (before.id === id && before.room ? {
          ...before,
          room: {
            ...before.room,
            participants: before.room.participants.map((participant) => (
              participant.userId === event.userId ? { ...participant, rttMs: event.rttMs } : participant)),
          },
        } : before));
      });
      refresh();
    });
    return () => {
      current = false;
      stopListening();
      subscription?.unsubscribe();
    };
  }, [roomId, id, fetchRoom]);

  const participants = room?.participants;
  useEffect(() => {
    if (participants) setRoomParticipants(participants);
  }, [participants]);

  const leave = async () => {
    setConfirmLeave(false);
    setLeaving(true);
    if (await leaveRoom(id)) {
      navigate('/rooms', { replace: true });
    } else {
      setLeaving(false);
    }
  };

  // The lesson events reload the lesson for everyone; the host's own change shows at once, without waiting for them
  const start = async (tab) => {
    const started = await startLesson(roomId, tab.id);
    if (started) {
      setDialog(null);
      reloadLesson(started);
    }
    return Boolean(started);
  };
  const startPlanned = async () => {
    setStarting(true);
    await start(plannedTab);
    setStarting(false);
  };
  const changeTab = async (tab) => {
    const changed = await changeLessonTab(roomId, tab.id);
    if (changed) {
      setDialog(null);
      reloadLesson(changed);
    }
    return Boolean(changed);
  };
  const end = async () => {
    setDialog(null);
    if (await endLesson(roomId)) reloadLesson(null);
  };

  if (error) {
    return (
      <div className="max-w-md space-y-4 pt-12">
        <h1 className="text-2xl font-black tracking-tighter">Room not available</h1>
        <p className="text-sm text-zinc-600 dark:text-zinc-400">{error}</p>
        <Link to="/rooms" className="text-sm underline">Go to my rooms</Link>
      </div>
    );
  }
  if (!room) {
    return <Loader2 size={20} className="animate-spin text-zinc-600 dark:text-zinc-400" />;
  }

  const open = room.status === 'OPEN';
  const others = room.participants.length - 1;
  const leaveMessage = isHost && open
    ? (others > 0
      ? 'The participant who joined earliest becomes the host, and playback pauses.'
      : 'You are the last one in the room, so it closes.')
    : 'You can come back with the invite link while the room is open.';

  let placeholder;
  if (!open) {
    placeholder = 'The room is closed.';
  } else if (!lessonLoaded) {
    placeholder = <Loader2 size={20} className="animate-spin" />;
  } else if (lessonError) {
    placeholder = lessonError;
  } else if (!isHost) {
    placeholder = 'Waiting for the host to start the lesson.';
  } else {
    placeholder = (
      <div className="max-w-sm space-y-4">
        {plannedTab ? (
          <>
            <p>
              Start the lesson with <span className="font-bold text-zinc-900 dark:text-white">{plannedTab.title}</span>
              {plannedTab.artist && ` · ${plannedTab.artist}`} when everyone is here.
            </p>
            <div className="flex flex-wrap justify-center gap-3">
              <button type="button" onClick={startPlanned} disabled={starting} className={primaryButtonClass}>
                {starting ? <Loader2 size={14} className="animate-spin" /> : <Play size={14} />} Start lesson
              </button>
              <button type="button" onClick={() => setDialog('start')} disabled={starting} className={secondaryButtonClass}>
                Another tab
              </button>
            </div>
          </>
        ) : (
          <>
            <p>Pick a tab and start the lesson when everyone is here.</p>
            <button type="button" onClick={() => setDialog('start')} className={`${primaryButtonClass} mx-auto`}>
              <Play size={14} /> Pick a tab and start
            </button>
          </>
        )}
        <p className="text-xs">{FREE_LIMIT_NOTE}</p>
      </div>
    );
  }

  return (
    <div className="space-y-8 pb-32">
      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-zinc-200 pb-6 dark:border-zinc-800">
        <div>
          <h1 className="text-4xl font-black tracking-tighter">{room.name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className={badgeClass}>{isHost ? 'You host' : 'Participant'}</span>
            {!open && <span className={badgeClass}>Closed</span>}
            <span className="text-xs text-zinc-600 dark:text-zinc-400">
              {room.participants.length} of {room.capacity} in the room
            </span>
          </div>
        </div>
        <button type="button" onClick={() => setConfirmLeave(true)} disabled={leaving} className={secondaryButtonClass}>
          {leaving ? <Loader2 size={14} className="animate-spin" /> : <LogOut size={14} />} Leave room
        </button>
      </header>

      {open && <RoomHints isHost={isHost} roomPath={`/rooms/${room.id}`} />}

      <div className="grid gap-8 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="min-w-0 space-y-3">
          <LessonCanvas lesson={open ? lesson : null} placeholder={placeholder} />
          {debug && <SyncDebugOverlay />}
        </div>

        <aside className="space-y-10">
          {open && lesson && (
            <LessonPanel
              key={lesson.id}
              roomId={room.id}
              lesson={lesson}
              isHost={isHost}
              onChangeTab={() => setDialog('change')}
              onEndLesson={() => setDialog('end')}
            />
          )}

          {isHost && open && room.inviteCode && (
            <section className="space-y-3">
              <h2 className={sectionTitleClass}>Invite link</h2>
              <InviteLink code={room.inviteCode} />
            </section>
          )}

          {open && <AudioPanel />}

          <ParticipantList participants={room.participants} capacity={room.capacity} userId={userId} />

          <RoomChat roomId={room.id} userId={userId} open={open} />
        </aside>
      </div>

      <LessonTabDialog
        isOpen={dialog === 'start' && isHost && open && !lesson}
        title="Start the lesson"
        confirmText="Start lesson"
        note={FREE_LIMIT_NOTE}
        initialTab={plannedTab ?? null}
        onConfirm={start}
        onCancel={() => setDialog(null)}
      />
      <LessonTabDialog
        isOpen={dialog === 'change' && isHost && open && Boolean(lesson)}
        title="Change the tab"
        confirmText="Show this tab"
        note="Playback starts over from the first measure, stopped, at the new tab's tempo."
        onConfirm={changeTab}
        onCancel={() => setDialog(null)}
      />
      <ConfirmDialog
        isOpen={dialog === 'end' && isHost && Boolean(lesson)}
        title="End the lesson?"
        message="The lesson ends for everyone. The room stays open, so you can start another one."
        confirmText="End lesson"
        onConfirm={end}
        onCancel={() => setDialog(null)}
      />
      <ConfirmDialog
        isOpen={confirmLeave}
        title="Leave the room?"
        message={leaveMessage}
        confirmText="Leave"
        onConfirm={leave}
        onCancel={() => setConfirmLeave(false)}
      />
    </div>
  );
}
