/**
 * The pure parts of the room chat (KAN-51): the messages shown, oldest first, put together from history pages
 * (newest first) and live messages, which can overlap or come twice after a reconnect.
 */

/** The longest message the server takes, in characters after trimming. */
export const MAX_MESSAGE_LENGTH = 2000;

/**
 * `current` with `incoming` added, each message once (by id; a later copy wins), oldest first.
 */
export function mergeMessages(current, incoming) {
  const byId = new Map(current.map((message) => [message.id, message]));
  incoming.forEach((message) => byId.set(message.id, message));
  return [...byId.values()].sort((a, b) => {
    const order = Date.parse(a.sentAt) - Date.parse(b.sentAt);
    if (order !== 0) return order;
    // Within one millisecond the server's ISO strings still differ in the microseconds, and equal instants are
    // ordered by id, as the server does (newest first there, so oldest first here)
    return a.sentAt.localeCompare(b.sentAt) || a.id.localeCompare(b.id);
  });
}

/**
 * What the text box holds as a message to send: the trimmed text, or null if it is empty or too long.
 */
export function sendableText(text) {
  const trimmed = (text ?? '').trim();
  return trimmed && trimmed.length <= MAX_MESSAGE_LENGTH ? trimmed : null;
}

/**
 * Whether two messages in a row come from the same author within 5 minutes, so the second one needs no name line.
 */
export function continuesPrevious(previous, message) {
  return Boolean(previous) && previous.authorId === message.authorId
    && Date.parse(message.sentAt) - Date.parse(previous.sentAt) < 5 * 60 * 1000;
}
