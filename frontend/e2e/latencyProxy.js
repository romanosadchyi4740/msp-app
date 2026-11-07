import net from 'node:net';

/**
 * A TCP proxy that delays everything passing through by a random 30-80 ms (by default) in each direction, like a
 * real network between a participant and the server. Each connection keeps its order: a chunk is never released
 * before the one sent before it, so jitter never reorders a WebSocket stream. Returns `{ close() }` once listening.
 */
export function startLatencyProxy({ port, targetHost, targetPort, minDelayMs = 30, maxDelayMs = 80 }) {
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
    server.listen(port, '127.0.0.1', () => resolve({
      close: () => new Promise((done) => {
        sockets.forEach((socket) => socket.destroy());
        server.close(() => done());
      }),
    }));
  });
}

function relayWithDelay(from, to, minDelayMs, maxDelayMs) {
  let releaseAt = 0;
  const later = (action) => {
    const now = Date.now();
    releaseAt = Math.max(releaseAt, now + minDelayMs + Math.random() * (maxDelayMs - minDelayMs));
    setTimeout(action, releaseAt - now);
  };
  from.on('data', (chunk) => later(() => {
    if (!to.destroyed) to.write(chunk);
  }));
  from.on('end', () => later(() => to.end()));
}
