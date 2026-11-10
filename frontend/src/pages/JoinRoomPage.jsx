import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Loader2, Users } from 'lucide-react';
import useRoomStore from '../store/roomStore';
import { errorMessage, primaryButtonClass, secondaryButtonClass } from '../components/rooms/roomStyles';
import { browserSupport, isCompatible } from '../rtc/deviceCheck';

/**
 * Opened from an invite link (`/join/:code`). A signed-out user is sent to sign in or register first and comes back
 * here; then the room is shown before joining it, with the way to the device check (KAN-49) and a warning if this
 * browser cannot take part in the room's audio.
 */
export default function JoinRoomPage() {
  const { code } = useParams();
  const navigate = useNavigate();
  const fetchPreview = useRoomStore((state) => state.fetchPreview);
  const joinRoom = useRoomStore((state) => state.joinRoom);
  // The preview of the code it was loaded for, so a different code shows the spinner until its own preview arrives
  const [loaded, setLoaded] = useState({ code: null, preview: null, error: null });
  const [joinError, setJoinError] = useState(null);
  const [joining, setJoining] = useState(false);

  useEffect(() => {
    let current = true;
    fetchPreview(code)
      .then((preview) => current && setLoaded({ code, preview, error: null }))
      .catch((failure) => current && setLoaded({
        code,
        preview: null,
        error: failure.response?.status === 404
          ? 'This invite link is invalid. The host may have replaced it with a new one.'
          : errorMessage(failure, 'Could not open the invite.'),
      }));
    return () => {
      current = false;
    };
  }, [code, fetchPreview]);

  const preview = loaded.code === code ? loaded.preview : null;
  const error = (joinError?.code === code ? joinError.message : null) || (loaded.code === code ? loaded.error : null);

  const join = async () => {
    setJoining(true);
    try {
      const room = await joinRoom(code);
      navigate(`/rooms/${room.id}`, { replace: true });
    } catch (failure) {
      setJoinError({ code, message: errorMessage(failure, 'Could not join the room.') });
      setJoining(false);
    }
  };

  const closed = preview?.status === 'CLOSED';
  const deviceCheck = `/device-check?next=${encodeURIComponent(`/join/${code}`)}`;
  const compatible = isCompatible(browserSupport(window));
  const blocked = closed ? 'The room is closed.' : preview?.full ? 'The room is full.' : null;

  return (
    <div className="mx-auto max-w-md space-y-8 pt-12 pb-32">
      {error ? (
        <div className="space-y-4 border border-zinc-200 p-8 dark:border-zinc-800">
          <h1 className="text-2xl font-black tracking-tighter">Cannot join</h1>
          <p className="text-sm text-zinc-500 dark:text-zinc-400">{error}</p>
          <Link to="/rooms" className="text-sm underline">Go to my rooms</Link>
        </div>
      ) : !preview ? (
        <Loader2 size={20} className="animate-spin text-zinc-500" />
      ) : (
        <div className="space-y-6 border border-zinc-200 p-8 dark:border-zinc-800">
          <div className="flex items-center gap-3 text-zinc-500 dark:text-zinc-400">
            <Users size={18} />
            <span className="text-xs font-black uppercase tracking-[0.2em]">You are invited to a room</span>
          </div>
          <div>
            <h1 className="text-3xl font-black tracking-tighter">{preview.name}</h1>
            <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
              Host: {preview.hostName || 'Unknown'} · {preview.participantCount} of {preview.capacity} in the room
            </p>
          </div>
          {blocked && <p className="text-sm font-bold text-red-600 dark:text-red-500">{blocked}</p>}
          <button
            type="button"
            onClick={join}
            disabled={joining || closed}
            className={`${primaryButtonClass} w-full`}
          >
            {joining ? <Loader2 size={16} className="animate-spin" /> : 'Join room'}
          </button>
          {!closed && (
            <>
              {!compatible && (
                <p role="alert" className="text-sm font-bold text-red-600 dark:text-red-500">
                  This browser cannot take part in the room&apos;s audio. The device check says what to do.
                </p>
              )}
              <Link to={deviceCheck} className={`${secondaryButtonClass} w-full`}>
                Check microphone and sound first
              </Link>
            </>
          )}
          {preview.full && !closed && (
            <p className="text-xs text-zinc-500 dark:text-zinc-400">
              If you are already in this room, joining opens it.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
