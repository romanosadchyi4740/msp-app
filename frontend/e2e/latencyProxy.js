import net from 'node:net';

/**
 * A TCP proxy that delays everything passing through by a random 30-80 ms (by default) in each direction, like a
 * real network between a participant and the server. Each direction of a connection is a FIFO queue drained by one
 * timer, so a chunk is never written before the one received before it: jitter never reorders a WebSocket stream.
 * Returns `{ port, close() }` once listening (port 0 picks a free one).
 */
export function startLatencyProxy({ port, host = '127.0.0.1', targetHost, targetPort, minDelayMs = 30,
  maxDelayMs = 80 }) {
  const sockets = new Set();
  const server = net.createServer((client) => {
    const upstream = net.connect(targetPort, targetHost);
    sockets.add(client);
    sockets.add(upstream);
    const close = () => {
      client.destroy();
      upstream.destroy();
      sockets.delete(client);
      sockets.delete(upstream);
    };
    for (const socket of [client, upstream]) {
      socket.on('error', close);
      socket.on('close', close);
    }
    relayWithDelay(client, upstream, minDelayMs, maxDelayMs);
    relayWithDelay(upstream, client, minDelayMs, maxDelayMs);
  });

  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, host, () => resolve({
      port: server.address().port,
      close: () => new Promise((done) => {
        sockets.forEach((socket) => socket.destroy());
        server.close(() => done());
      }),
    }));
  });
}

/**
 * Relays `from` to `to`, each chunk (and the end) held until its release time. Release times never decrease, and
 * one timer writes the queue's head in order, so the order of the stream is kept.
 */
function relayWithDelay(from, to, minDelayMs, maxDelayMs) {
  const queue = [];
  let lastReleaseAt = 0;
  let timer = null;

  const drain = () => {
    timer = null;
    const now = Date.now();
    while (queue.length > 0 && queue[0].releaseAt <= now) {
      const { chunk } = queue.shift();
      if (to.destroyed) continue;
      if (chunk === null) to.end();
      else to.write(chunk);
    }
    if (queue.length > 0) timer = setTimeout(drain, queue[0].releaseAt - now);
  };

  const enqueue = (chunk) => {
    const now = Date.now();
    lastReleaseAt = Math.max(lastReleaseAt, now + minDelayMs + Math.random() * (maxDelayMs - minDelayMs));
    queue.push({ chunk, releaseAt: lastReleaseAt });
    if (timer === null) timer = setTimeout(drain, queue[0].releaseAt - now);
  };

  from.on('data', (chunk) => enqueue(chunk));
  from.on('end', () => enqueue(null));
}
