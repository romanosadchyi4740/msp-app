import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { Loader2, Send } from 'lucide-react';
import toast from 'react-hot-toast';
import useRoomStore from '../../store/roomStore';
import socketClient from '../../api/socketClient';
import { continuesPrevious, MAX_MESSAGE_LENGTH, mergeMessages, sendableText } from './chatMessages';
import { errorMessage, sectionTitleClass } from './roomStyles';

const PAGE_SIZE = 50;
/** How close to the bottom (px) the list must be to follow new messages. */
const FOLLOW_THRESHOLD_PX = 80;

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit' });

/**
 * The room's text chat (KAN-51, FR-12) beside the lesson: the latest messages, older ones on request, new ones live
 * from `/topic/rooms/{id}/chat`. Every (re)connect reads the latest page again, since messages sent while the
 * connection was down are not replayed. Sending goes over STOMP; a refused message is answered on this user's
 * `/user/queue/rooms/{id}/chat-errors`, and the refused text comes back into the empty text box. A closed room's
 * history stays readable.
 *
 * Known limits: older pages are read by offset, so a page can skip messages when an author's account is deleted while
 * someone pages back; and a reconnect reads only the latest page, so more than 50 messages missed while offline leave
 * a gap until the page is opened again.
 */
export default function RoomChat({ roomId, userId, open }) {
  const fetchRoomMessages = useRoomStore((state) => state.fetchRoomMessages);
  const [messages, setMessages] = useState([]);
  // The next older page to read, or null once the oldest was read
  const [olderPage, setOlderPage] = useState(1);
  const [loadingOlder, setLoadingOlder] = useState(false);
  const [error, setError] = useState(null);
  const [loaded, setLoaded] = useState(false);
  const [text, setText] = useState('');
  const listRef = useRef(null);
  // Whether the list should stay at the bottom when messages are added
  const follow = useRef(true);
  // The text last sent, given back to the text box if the server refuses it
  const lastSent = useRef('');

  const addMessages = useCallback((incoming) => setMessages((current) => mergeMessages(current, incoming)), []);

  useEffect(() => {
    let current = true;
    let subscriptions = [];
    const readLatest = () => fetchRoomMessages(roomId, 0, PAGE_SIZE)
      .then((page) => {
        if (!current) return;
        addMessages(page.content);
        // Only the first read decides whether there are older pages; later reads only catch up
        setOlderPage((before) => (before === 1 && page.last ? null : before));
        setError(null);
        setLoaded(true);
      })
      .catch((failure) => {
        if (!current) return;
        setError(errorMessage(failure, 'Could not load the chat.'));
        setLoaded(true);
      });
    const stopListening = socketClient.onConnect(() => {
      subscriptions.forEach((subscription) => subscription?.unsubscribe());
      subscriptions = [
        socketClient.subscribe(`/topic/rooms/${roomId}/chat`, (message) => addMessages([message])),
        socketClient.subscribe(`/user/queue/rooms/${roomId}/chat-errors`, (refused) => {
          toast.error(refused?.message || 'The message was not sent.');
          // Not over anything typed since
          setText((typed) => (typed ? typed : lastSent.current));
        }),
      ];
      readLatest();
    });
    return () => {
      current = false;
      stopListening();
      subscriptions.forEach((subscription) => subscription?.unsubscribe());
    };
  }, [roomId, fetchRoomMessages, addMessages]);

  // New messages keep the list at the bottom if it was there; older ones keep what was on screen in place
  const previousHeight = useRef(0);
  const loadingOlderRef = useRef(false);
  useLayoutEffect(() => {
    const list = listRef.current;
    if (!list) return;
    if (follow.current) {
      list.scrollTop = list.scrollHeight;
    } else if (loadingOlderRef.current) {
      list.scrollTop += list.scrollHeight - previousHeight.current;
    }
    loadingOlderRef.current = false;
    previousHeight.current = list.scrollHeight;
  }, [messages]);

  const onScroll = () => {
    const list = listRef.current;
    follow.current = list.scrollHeight - list.scrollTop - list.clientHeight < FOLLOW_THRESHOLD_PX;
  };

  const loadOlder = async () => {
    if (olderPage === null) return;
    setLoadingOlder(true);
    try {
      const page = await fetchRoomMessages(roomId, olderPage, PAGE_SIZE);
      follow.current = false;
      loadingOlderRef.current = true;
      previousHeight.current = listRef.current?.scrollHeight ?? 0;
      addMessages(page.content);
      setOlderPage(page.last ? null : olderPage + 1);
    } catch (failure) {
      toast.error(errorMessage(failure, 'Could not load older messages.'));
    } finally {
      setLoadingOlder(false);
    }
  };

  const content = sendableText(text);
  const tooLong = text.trim().length > MAX_MESSAGE_LENGTH;
  const send = (event) => {
    event.preventDefault();
    if (!content || !open) return;
    if (!socketClient.sendMessage(`/rooms/${roomId}/chat`, { content })) {
      toast.error('Not connected to the room; the message was not sent.');
      return;
    }
    follow.current = true;
    lastSent.current = content;
    setText('');
  };

  return (
    <section className="space-y-3" aria-label="Room chat">
      <div className="flex items-center justify-between gap-3">
        <h2 className={sectionTitleClass}>Chat</h2>
        {olderPage !== null && loaded && !error && (
          <button
            type="button"
            onClick={loadOlder}
            disabled={loadingOlder}
            className="flex items-center gap-2 text-[10px] font-black uppercase tracking-widest text-zinc-600 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white"
          >
            {loadingOlder && <Loader2 size={12} className="animate-spin" />} Load earlier
          </button>
        )}
      </div>
      {!loaded && <Loader2 size={16} className="animate-spin text-zinc-600 dark:text-zinc-400" />}
      {error && <p role="alert" className="text-xs text-zinc-600 dark:text-zinc-400">{error}</p>}
      {/* A log announces what is added; older pages are added only on request */}
      <div
        ref={listRef}
        onScroll={onScroll}
        className="h-72 space-y-1 overflow-y-auto border border-zinc-200 p-3 dark:border-zinc-800"
        role="log"
        aria-label="Messages"
        aria-busy={loadingOlder}
      >
        {loaded && !error && messages.length === 0 && (
          <p className="text-xs text-zinc-600 dark:text-zinc-400">No messages yet.</p>
        )}
        {messages.map((message, index) => {
          const mine = message.authorId === userId;
          const continued = continuesPrevious(messages[index - 1], message);
          return (
            <div key={message.id} className={continued ? '' : 'pt-2'}>
              {!continued && (
                <p className="flex items-baseline gap-2 text-[10px] text-zinc-600 dark:text-zinc-400">
                  <span className="font-black uppercase tracking-widest">
                    {mine ? 'You' : (message.authorName || 'Unknown user')}
                  </span>
                  <time dateTime={message.sentAt}>{timeFormat.format(new Date(message.sentAt))}</time>
                </p>
              )}
              <p className="whitespace-pre-wrap break-words text-sm">{message.content}</p>
            </div>
          );
        })}
      </div>
      {open ? (
        <form onSubmit={send} className="space-y-1">
          <div className="flex gap-2">
            <textarea
              value={text}
              onChange={(event) => setText(event.target.value)}
              onKeyDown={(event) => {
                // Enter sends, Shift+Enter starts a new line
                if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) send(event);
              }}
              rows={2}
              placeholder="Message the room"
              aria-label="Message"
              aria-invalid={tooLong}
              className="min-w-0 flex-1 resize-none border border-zinc-300 bg-transparent px-3 py-2 text-sm outline-none focus:border-zinc-900 dark:border-zinc-700 dark:focus:border-white"
            />
            <button
              type="submit"
              disabled={!content}
              aria-label="Send"
              className="flex w-12 items-center justify-center bg-zinc-900 text-white hover:bg-zinc-700 disabled:opacity-40 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
            >
              <Send size={16} />
            </button>
          </div>
          {tooLong && (
            <p className="text-xs text-red-600 dark:text-red-400">
              At most {MAX_MESSAGE_LENGTH} characters ({text.trim().length} now).
            </p>
          )}
        </form>
      ) : (
        <p className="text-xs text-zinc-600 dark:text-zinc-400">The room is closed; the chat is read-only.</p>
      )}
    </section>
  );
}
