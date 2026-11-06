import { useState } from 'react';
import { Link } from 'react-router-dom';
import { ArrowRight, Loader2 } from 'lucide-react';
import useRoomStore from '../store/roomStore';
import TabPicker from '../components/rooms/TabPicker';
import InviteLink from '../components/rooms/InviteLink';
import { inputClass, labelClass } from '../components/tabs/tabDetails';
import { primaryButtonClass, sectionTitleClass } from '../components/rooms/roomStyles';

const NAME_MAX_LENGTH = 100;

/**
 * Creating a room: a name and a tab, then the invite link to send. The tab is only remembered; the lesson starts
 * from the room page.
 */
export default function RoomNewPage() {
  const createRoom = useRoomStore((state) => state.createRoom);
  const [name, setName] = useState('');
  const [tab, setTab] = useState(null);
  const [creating, setCreating] = useState(false);
  const [room, setRoom] = useState(null);

  const submit = async (event) => {
    event.preventDefault();
    setCreating(true);
    const created = await createRoom(name.trim(), tab);
    setCreating(false);
    if (created) {
      setRoom(created);
    }
  };

  if (room) {
    return (
      <div className="max-w-2xl space-y-10 pb-32">
        <header className="border-b border-zinc-200 pb-8 dark:border-zinc-800">
          <h1 className="text-5xl font-black tracking-tighter">{room.name}</h1>
          <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
            The room is ready. Send this link to up to {room.capacity - 1} players; they join after signing in.
          </p>
        </header>
        <section className="space-y-3">
          <h2 className={sectionTitleClass}>Invite link</h2>
          <InviteLink code={room.inviteCode} />
        </section>
        {tab && (
          <p className="text-sm text-zinc-500 dark:text-zinc-400">
            Tab for the lesson: <span className="font-bold text-zinc-900 dark:text-white">{tab.title}</span>
          </p>
        )}
        <Link to={`/rooms/${room.id}`} className={`${primaryButtonClass} w-fit`}>
          Open the room <ArrowRight size={16} />
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={submit} className="max-w-2xl space-y-10 pb-32">
      <header className="border-b border-zinc-200 pb-8 dark:border-zinc-800">
        <h1 className="text-5xl font-black tracking-tighter">New room</h1>
        <p className="mt-2 text-sm text-zinc-500 dark:text-zinc-400">
          Name the room and pick the tab to play; you get a link to invite the others.
        </p>
      </header>

      <div>
        <label htmlFor="room-name" className={labelClass}>Name</label>
        <input
          id="room-name"
          className={inputClass}
          placeholder="Tuesday lesson"
          maxLength={NAME_MAX_LENGTH}
          required
          autoFocus
          value={name}
          onChange={(event) => setName(event.target.value)}
        />
      </div>

      <section className="space-y-3">
        <h2 className={sectionTitleClass}>Tab</h2>
        <p className="text-xs text-zinc-500 dark:text-zinc-400">
          {tab ? <>Picked: <span className="font-bold text-zinc-900 dark:text-white">{tab.title}</span></>
            : 'Optional; you can pick it in the room too.'}
        </p>
        <TabPicker selected={tab} onSelect={setTab} />
      </section>

      <button type="submit" disabled={creating || !name.trim()} className={primaryButtonClass}>
        {creating ? <Loader2 size={16} className="animate-spin" /> : 'Create room'}
      </button>
    </form>
  );
}
