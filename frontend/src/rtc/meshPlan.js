/**
 * The decisions of the audio mesh (KAN-47) that need no browser: who yields when two offers cross, who starts a
 * connection, and which connections to open or close for the people in the room.
 */

/** A room holds the host and 4 others, so a participant has at most this many connections. */
export const MAX_PEERS = 4;

/**
 * The "perfect negotiation" roles, from the two user ids so both sides agree without talking: of two participants
 * the one with the greater id is polite, i.e. withdraws its own offer when the two cross.
 */
export function isPolite(myId, peerId) {
  return myId > peerId;
}

/**
 * Whether this side opens the connection to the peer. Only one side does, the impolite one, so a connection always
 * starts with exactly one offer; the other side answers it. Later either side may renegotiate.
 */
export function isInitiator(myId, peerId) {
  return !isPolite(myId, peerId);
}

/**
 * What to do with the connections, given who is in the room.
 *
 * - `participants`: the room's participants as `{ userId, online }`, with or without this user.
 * - `connections`: the peers there is a connection to, as `{ userId, alive }`; `alive` while audio can still flow
 *   (connected, or still connecting).
 *
 * Returns `{ open, close }`, lists of user ids:
 * - `close`: connections to people who left the room, and dead connections to people who are offline. A live
 *   connection to someone offline stays: audio goes peer to peer, so it survives a peer's short loss of the server.
 * - `open`: online participants without a connection whom this user is to call (see `isInitiator`), up to
 *   `MAX_PEERS` connections in all.
 */
export function planConnections(myId, participants, connections) {
  const others = participants.filter((participant) => participant.userId !== myId);
  const byId = new Map(others.map((participant) => [participant.userId, participant]));

  const close = connections
    .filter((connection) => {
      const participant = byId.get(connection.userId);
      return !participant || (!participant.online && !connection.alive);
    })
    .map((connection) => connection.userId);

  const kept = new Set(connections.map((connection) => connection.userId).filter((userId) => !close.includes(userId)));
  const open = [];
  for (const participant of others) {
    if (kept.size + open.length >= MAX_PEERS) break;
    if (participant.online && !kept.has(participant.userId) && isInitiator(myId, participant.userId)) {
      open.push(participant.userId);
    }
  }
  return { open, close };
}
