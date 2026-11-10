import dgram from 'node:dgram';

/**
 * A UDP proxy in front of a TURN server that can lose packets (KAN-48): browsers in relay-only mode that use it as
 * their TURN address send all their audio through it, so dropping here is real packet loss for them.
 *
 * Only relayed data is dropped (TURN ChannelData, whose first byte is 0x40-0x7F); the TURN requests that keep the
 * allocation alive always pass, so the connection survives the loss. Each client gets its own socket towards the
 * server, as the server tells clients apart by their address.
 *
 * Returns `{ port, setLossPercent(percent), dropped(), close() }` once listening (port 0 picks a free one).
 */
export function startLossyUdpProxy({ port = 0, host = '127.0.0.1', targetHost, targetPort, random = Math.random }) {
  const server = dgram.createSocket('udp4');
  const upstreams = new Map();
  let lossPercent = 0;
  let dropped = 0;

  const lose = (message) => {
    const relayedData = message.length > 0 && message[0] >= 0x40 && message[0] <= 0x7f;
    if (relayedData && random() * 100 < lossPercent) {
      dropped += 1;
      return true;
    }
    return false;
  };

  server.on('message', (message, client) => {
    const key = `${client.address}:${client.port}`;
    let upstream = upstreams.get(key);
    if (!upstream) {
      upstream = dgram.createSocket('udp4');
      upstream.on('message', (reply) => {
        if (!lose(reply)) server.send(reply, client.port, client.address);
      });
      upstream.on('error', () => {});
      upstreams.set(key, upstream);
    }
    if (!lose(message)) upstream.send(message, targetPort, targetHost);
  });
  server.on('error', () => {});

  return new Promise((resolve, reject) => {
    server.once('error', reject);
    server.bind(port, host, () => {
      resolve({
        port: server.address().port,
        setLossPercent: (percent) => {
          lossPercent = percent;
        },
        dropped: () => dropped,
        close: () => new Promise((done) => {
          upstreams.forEach((upstream) => upstream.close());
          upstreams.clear();
          server.close(done);
        }),
      });
    });
  });
}
