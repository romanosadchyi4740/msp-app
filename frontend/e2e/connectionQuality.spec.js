import { randomUUID } from 'node:crypto';
import { networkInterfaces } from 'node:os';
import { expect, request, test } from '@playwright/test';
import { API_URL, PROXY_PORT } from './e2eConfig.js';
import { startLatencyProxy } from './latencyProxy.js';
import { startLossyUdpProxy } from './lossyUdpProxy.js';

/**
 * The connection quality indicator (KAN-48, FR-13): two participants with their microphones on see each other's
 * connection as good (green); when a third of the audio packets between them is lost, the indicator changes colour,
 * and it turns green again when the loss stops. The reported round-trip average must also reach the room.
 *
 * The loss is real: both browsers are in relay-only mode and are given, instead of the local coturn's address, a
 * UDP proxy in front of it that drops relayed packets (e2e/lossyUdpProxy.js). Chrome's own network emulation
 * (DevTools `Network.emulateNetworkConditions` with `packetLoss`) was tried first and had no effect on these
 * connections. The relay marker is checked on the way, since the audio goes through the relay.
 * The test is skipped on a machine with no network interface besides loopback.
 *
 * Needs the backend and the local coturn: `docker compose --profile turn up -d`.
 */

const PACKET_LOSS_PERCENT = 35;
// The coturn of docker-compose.yml
const TURN_HOST = '127.0.0.1';
const TURN_PORT = 3478;
// Once a page may use the microphone, Chrome gathers candidates on the real network interfaces only, and from
// those a UDP TURN server on 127.0.0.1 cannot be reached; so the proxy is addressed by this machine's own address
const LAN_ADDRESS = Object.values(networkInterfaces()).flat()
  .find((address) => address.family === 'IPv4' && !address.internal)?.address;

let proxy;
let turnProxy;
let api;
const users = [];
let roomId;

test.beforeAll(async () => {
  proxy = await startLatencyProxy({
    port: PROXY_PORT,
    targetHost: new URL(API_URL).hostname,
    targetPort: Number(new URL(API_URL).port || 80),
  });
  turnProxy = await startLossyUdpProxy({ host: '0.0.0.0', targetHost: TURN_HOST, targetPort: TURN_PORT });
  api = await request.newContext({ baseURL: API_URL });
  for (const name of ['Quality host', 'Quality participant']) {
    users.push(await register(name));
  }
  const room = await post('/api/rooms', users[0], { name: 'Quality check' });
  roomId = room.id;
  await post(`/api/rooms/join/${encodeURIComponent(room.inviteCode)}`, users[1]);
});

test.afterAll(async () => {
  for (const user of users) {
    await api?.delete('/api/users/me', { headers: { Authorization: `Bearer ${user.token}` } });
  }
  await api?.dispose();
  await proxy?.close();
  await turnProxy?.close();
});

test('the indicator is green on a clean connection and changes colour under packet loss', async ({ browser }) => {
  test.skip(!LAN_ADDRESS, 'needs a network interface besides loopback');
  const pages = [];
  for (const user of users) {
    const context = await browser.newContext();
    await context.addInitScript(({ token, profile }) => {
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(profile));
      // The participant's "Relay only" setting
      localStorage.setItem('audio-settings', JSON.stringify({ state: { relayOnly: true }, version: 0 }));
    }, { token: user.token, profile: user.profile });
    // The server's TURN credentials, with the lossy proxy as the TURN address
    await context.route('**/api/rtc/ice-servers', async (route) => {
      // The CORS preflight has no body to rewrite
      if (route.request().method() !== 'GET') {
        await route.continue();
        return;
      }
      const response = await route.fetch();
      const body = await response.json();
      const iceServers = body.iceServers
        .filter((server) => server.username)
        .map((server) => ({ ...server, urls: [`turn:${LAN_ADDRESS}:${turnProxy.port}?transport=udp`] }));
      await route.fulfill({ response, json: { ...body, iceServers } });
    });
    const page = await context.newPage();
    await page.goto(`/rooms/${roomId}?debug=1`);
    pages.push(page);
  }
  const [hostPage] = pages;
  for (const page of pages) {
    await page.waitForFunction(() => Boolean(window.__syncchordRtc), null, { timeout: 30_000 });
    await page.evaluate(() => window.__syncchordRtc.setMicrophone(true));
  }

  // A clean local connection is graded good on both sides
  for (const page of pages) {
    await expect(page.locator('[data-quality="good"]')).toHaveCount(1, { timeout: 30_000 });
  }

  // Every 10 seconds each browser reports its average round trip, and the room shows it for both participants
  await expect(hostPage.getByText(/^avg \d+ ms$/)).toHaveCount(2, { timeout: 30_000 });

  // The audio goes through the relay, and the list says so
  await expect(hostPage.getByText('Relay', { exact: true })).toBeVisible();

  // A third of the audio packets between them gets lost: the dot next to the participant is no longer green
  turnProxy.setLossPercent(PACKET_LOSS_PERCENT);
  const degraded = hostPage.locator('[data-quality="fair"], [data-quality="poor"]');
  await expect(degraded).toHaveCount(1, { timeout: 30_000 });
  await expect(hostPage.locator('[data-quality="good"]')).toHaveCount(0);
  const grade = await degraded.getAttribute('data-quality');
  const figures = await degraded.locator('xpath=..').getAttribute('title');
  expect(turnProxy.dropped()).toBeGreaterThan(0);

  // And it is green again once the loss stops
  turnProxy.setLossPercent(0);
  await expect(hostPage.locator('[data-quality="good"]')).toHaveCount(1, { timeout: 30_000 });
  console.log(`Connection quality under ${PACKET_LOSS_PERCENT}% packet loss: ${grade} (${figures}); `
    + `good again after the loss stopped`);
});

async function register(displayName) {
  const email = `e2e-quality-${randomUUID()}@example.test`;
  const response = await api.post('/auth/register', {
    data: { email, password: `E2e-${randomUUID()}`, displayName },
  });
  expect(response.ok(), `registering ${displayName}`).toBeTruthy();
  const { accessToken, ...profile } = await response.json();
  return { token: accessToken, profile };
}

async function post(path, user, data) {
  const response = await api.post(path, { headers: { Authorization: `Bearer ${user.token}` }, data });
  expect(response.ok(), `POST ${path}: ${response.status()}`).toBeTruthy();
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
