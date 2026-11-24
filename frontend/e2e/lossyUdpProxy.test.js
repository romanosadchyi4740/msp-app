import { after, before, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import dgram from 'node:dgram';
import { startLossyUdpProxy } from './lossyUdpProxy.js';

// Relayed data starts with a channel number (0x4000-0x7FFF); a TURN request starts with 0x00
const DATA = Buffer.from([0x40, 0x01, 0x00, 0x02, 0xaa, 0xbb]);
const REQUEST = Buffer.from([0x00, 0x03, 0x00, 0x00]);

describe('startLossyUdpProxy', () => {
  let echo;
  let proxy;
  let client;
  let received;

  before(async () => {
    // Stands for the TURN server: sends back what it gets
    echo = dgram.createSocket('udp4');
    echo.on('message', (message, sender) => echo.send(message, sender.port, sender.address));
    await new Promise((resolve) => echo.bind(0, '127.0.0.1', resolve));
    proxy = await startLossyUdpProxy({ targetHost: '127.0.0.1', targetPort: echo.address().port });
    client = dgram.createSocket('udp4');
    received = [];
    client.on('message', (message) => received.push(message));
    await new Promise((resolve) => client.bind(0, '127.0.0.1', resolve));
  });

  after(async () => {
    client.close();
    await proxy.close();
    echo.close();
  });

  const exchange = async (messages) => {
    received = [];
    for (const message of messages) {
      client.send(message, proxy.port, '127.0.0.1');
    }
    await new Promise((resolve) => setTimeout(resolve, 200));
    return received;
  };

  test('without loss everything goes to the server and comes back', async () => {
    proxy.setLossPercent(0);

    const replies = await exchange([DATA, REQUEST, DATA]);

    assert.equal(replies.length, 3);
    assert.ok(replies.some((reply) => reply.equals(REQUEST)));
  });

  test('with full loss relayed data is dropped and requests still pass', async () => {
    proxy.setLossPercent(100);
    const before = proxy.dropped();

    const replies = await exchange([DATA, REQUEST, DATA]);

    assert.equal(replies.length, 1);
    assert.ok(replies[0].equals(REQUEST));
    assert.equal(proxy.dropped() - before, 2);
  });

  test('loss can be switched off again', async () => {
    proxy.setLossPercent(100);
    proxy.setLossPercent(0);

    assert.equal((await exchange([DATA])).length, 1);
  });
});
