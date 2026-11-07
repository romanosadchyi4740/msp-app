import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { Crown, Loader2, LogOut, User } from 'lucide-react';
import useRoomStore, { plannedTabOf } from '../store/roomStore';
import useAuthStore from '../store/authStore';
import InviteLink from '../components/rooms/InviteLink';
import ConfirmDialog from '../components/modals/ConfirmDialog';
import { badgeClass, errorMessage, secondaryButtonClass, sectionTitleClass } from '../components/rooms/roomStyles';
import LessonCanvas from '../components/rooms/LessonCanvas';
import { startSyncEngine } from '../sync/syncEngine';
import { startMetronome } from '../sync/metronome';
import { installSyncDebugHook } from '../sync/syncDebug';
import SyncDebugOverlay from '../components/rooms/SyncDebugOverlay';

/**
 * A room the user is in: who is in it, the invite link for the host, the tab picked for the lesson, and leaving.
 * The active lesson's tab follows the shared position (KAN-42); live presence and the host's lesson controls come
 * with the full room page.
 */
export default function RoomPage() {
  const { id } = useParams();
  // ?debug=1 shows the sync figures and exposes the hook the drift test reads (KAN-44)
  const [searchParams] = useSearchParams();
  const debug = searchParams.get('debug') === '1';
  const navigate = useNavigate();
  const fetchRoom = useRoomStore((state) => state.fetchRoom);
  const leaveRoom = useRoomStore((state) => state.leaveRoom);
  const plannedTab = useRoomStore((state) => plannedTabOf(state, id));
  const userId = useAuthStore((state) => state.user?.userId);
  // The room of the id it was loaded for, so another room shows the spinner until its own data arrives
  const [loaded, setLoaded] = useState({ id: null, room: null, error: null });
  const [confirmLeave, setConfirmLeave] = useState(false);
  const [leaving, setLeaving] = useState(false);

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

  const leave = async () => {
    setConfirmLeave(false);
    setLeaving(true);
    if (await leaveRoom(id)) {
      navigate('/rooms', { replace: true });
    } else {
      setLeaving(false);
    }
  };

  if (error) {
    return (
      <div className="max-w-md space-y-4 pt-12">
        <h1 className="text-2xl font-black tracking-tighter">Room not available</h1>
        <p className="text-sm text-zinc-500 dark:text-zinc-400">{error}</p>
        <Link to="/rooms" className="text-sm underline">Go to my rooms</Link>
      </div>
    );
  }
  if (!room) {
    return <Loader2 size={20} className="animate-spin text-zinc-500" />;
  }

  const isHost = room.myRole === 'HOST';
  const open = room.status === 'OPEN';
  const others = room.participants.length - 1;
  const leaveMessage = isHost && open
    ? (others > 0
      ? 'The participant who joined earliest becomes the host, and playback pauses.'
      : 'You are the last one in the room, so it closes.')
    : 'You can come back with the invite link while the room is open.';

  return (
    <div className="max-w-3xl space-y-12 pb-32">
      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-zinc-200 pb-8 dark:border-zinc-800">
        <div>
          <h1 className="text-5xl font-black tracking-tighter">{room.name}</h1>
          <div className="mt-3 flex flex-wrap items-center gap-2">
            <span className={badgeClass}>{isHost ? 'You host' : 'Participant'}</span>
            {!open && <span className={badgeClass}>Closed</span>}
            <span className="text-xs text-zinc-500 dark:text-zinc-400">
              {room.participants.length} of {room.capacity} in the room
            </span>
          </div>
        </div>
        <button type="button" onClick={() => setConfirmLeave(true)} disabled={leaving} className={secondaryButtonClass}>
          {leaving ? <Loader2 size={14} className="animate-spin" /> : <LogOut size={14} />} Leave room
        </button>
      </header>

      {isHost && open && room.inviteCode && (
        <section className="space-y-3">
          <h2 className={sectionTitleClass}>Invite link</h2>
          <InviteLink code={room.inviteCode} />
        </section>
      )}

      {isHost && plannedTab && (
        <section className="space-y-2">
          <h2 className={sectionTitleClass}>Tab for the lesson</h2>
          <Link to={`/tabs/${plannedTab.id}`} className="text-sm font-bold underline underline-offset-4">
            {plannedTab.title}
          </Link>
          {plannedTab.artist && <span className="text-sm text-zinc-500 dark:text-zinc-400"> · {plannedTab.artist}</span>}
        </section>
      )}

      <LessonCanvas roomId={room.id} />
      {debug && <SyncDebugOverlay />}

      <section className="space-y-3">
        <h2 className={sectionTitleClass}>Participants</h2>
        <ul className="space-y-2">
          {room.participants.map((participant) => (
            <li key={participant.userId} className="flex items-center gap-4 border border-zinc-200 p-3 dark:border-zinc-800">
              {participant.role === 'HOST'
                ? <Crown size={18} className="shrink-0" aria-label="Host" />
                : <User size={18} className="shrink-0 text-zinc-500 dark:text-zinc-400" />}
              <Link to={`/users/${participant.userId}`} className="min-w-0 flex-1 truncate text-sm font-bold hover:underline">
                {participant.displayName || 'Unknown'}
              </Link>
              {participant.userId === userId && <span className="text-xs text-zinc-500 dark:text-zinc-400">you</span>}
              {participant.role === 'HOST' && <span className={badgeClass}>Host</span>}
            </li>
          ))}
        </ul>
      </section>

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
