import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import net from 'node:net';
import { startLatencyProxy } from './latencyProxy.js';

let target;
let proxy;
let received = '';
let targetPort;

before(async () => {
  target = net.createServer((socket) => socket.on('data', (chunk) => {
    received += chunk.toString();
  }));
  await new Promise((resolve) => target.listen(0, '127.0.0.1', resolve));
  targetPort = target.address().port;
  proxy = await startLatencyProxy({ port: 0, targetHost: '127.0.0.1', targetPort, minDelayMs: 1, maxDelayMs: 25 });
});

after(async () => {
  await proxy.close();
  await new Promise((resolve) => target.close(resolve));
});

test('keeps the order of a burst of chunks despite the random delays', async () => {
  const client = net.connect(proxy.port, '127.0.0.1');
  await new Promise((resolve) => client.once('connect', resolve));
  const sent = [];
  for (let i = 0; i < 400; i += 1) {
    const chunk = `<${i}>`;
    sent.push(chunk);
    client.write(chunk);
    // Separate ticks, so the chunks reach the proxy one by one, each with its own random delay
    await new Promise((resolve) => setImmediate(resolve));
  }
  const expected = sent.join('');
  const deadline = Date.now() + 5_000;
  while (received.length < expected.length && Date.now() < deadline) {
    await new Promise((resolve) => setTimeout(resolve, 20));
  }
  client.destroy();

  assert.equal(received, expected);
});
