import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Loader2, Plus, Users } from 'lucide-react';
import { useShallow } from 'zustand/react/shallow';
import useRoomStore from '../store/roomStore';
import { badgeClass, primaryButtonClass } from '../components/rooms/roomStyles';

/**
 * The rooms the user hosts or has joined.
 */
export default function RoomsPage() {
  const { myRooms, myRoomsLoading, fetchMyRooms } = useRoomStore(useShallow((state) => ({
    myRooms: state.myRooms,
    myRoomsLoading: state.myRoomsLoading,
    fetchMyRooms: state.fetchMyRooms,
  })));

  useEffect(() => {
    fetchMyRooms();
  }, [fetchMyRooms]);

  return (
    <div className="space-y-12 pb-32">
      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-zinc-200 pb-8 dark:border-zinc-800">
        <div>
          <h1 className="text-5xl font-black tracking-tighter">Rooms</h1>
          <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
            Live lessons and rehearsals: one tab, one metronome, up to five players.
          </p>
        </div>
        <Link to="/rooms/new" className={primaryButtonClass}>
          <Plus size={16} /> New room
        </Link>
      </header>

      <section className="space-y-4">
        <h2 className="text-xs font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400">My rooms</h2>
        {myRoomsLoading && myRooms.length === 0 ? (
          <Loader2 size={20} className="animate-spin text-zinc-500" />
        ) : myRooms.length === 0 ? (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            You are in no rooms yet. <Link to="/rooms/new" className="underline">Create one</Link> or open an
            invite link someone sent you.
          </p>
        ) : (
          <div className="space-y-2">
            {myRooms.map((room) => (
              <Link
                key={room.id}
                to={`/rooms/${room.id}`}
                className="flex items-center gap-4 border border-zinc-200 p-4 transition-colors hover:border-zinc-900 dark:border-zinc-800 dark:hover:border-white"
              >
                <Users size={20} className="shrink-0 text-zinc-500 dark:text-zinc-400" />
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-bold">{room.name}</p>
                  <p className="truncate text-xs text-zinc-500 dark:text-zinc-400">
                    {room.myRole === 'HOST' ? 'You host' : `Host: ${room.hostName || 'Unknown'}`}
                    {' · '}
                    {room.participantCount} of {room.capacity}
                  </p>
                </div>
                <div className="flex shrink-0 flex-wrap items-center gap-2">
                  <span className={badgeClass}>{room.myRole === 'HOST' ? 'Host' : 'Participant'}</span>
                  {room.status === 'CLOSED' && <span className={badgeClass}>Closed</span>}
                </div>
              </Link>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
