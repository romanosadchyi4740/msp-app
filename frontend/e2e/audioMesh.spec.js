import { randomUUID } from 'node:crypto';
import { expect, request, test } from '@playwright/test';
import { API_URL, PROXY_PORT } from './e2eConfig.js';
import { startLatencyProxy } from './latencyProxy.js';

/**
 * The room's audio between three browsers (KAN-47, KAN-46; UR-4, FR-7): a host and two participants each open the
 * room, turn the microphone on (Chrome's fake one, a steady tone) and must receive audio from both others, sent as
 * music-tuned Opus. Then all three switch to relay-only and the audio must flow again, now through the TURN relay,
 * with the short-lived credentials the server hands out.
 *
 * Besides the backend it needs the local coturn: `docker compose --profile turn up -d`. Signaling goes through the
 * latency proxy like every STOMP connection of the e2e dev server; the audio itself goes browser to browser.
 */

const CONNECT_TIMEOUT_MS = 45_000;

let proxy;
let api;
const users = [];
let roomId;

test.beforeAll(async () => {
  proxy = await startLatencyProxy({
    port: PROXY_PORT,
    targetHost: new URL(API_URL).hostname,
    targetPort: Number(new URL(API_URL).port || 80),
  });
  api = await request.newContext({ baseURL: API_URL });
  for (const name of ['Audio host', 'Audio participant 1', 'Audio participant 2']) {
    users.push(await register(name));
  }
  const [host, ...participants] = users;
  const room = await post('/api/rooms', host, { name: 'Audio check' });
  roomId = room.id;
  for (const participant of participants) {
    await post(`/api/rooms/join/${encodeURIComponent(room.inviteCode)}`, participant);
  }
});

test.afterAll(async () => {
  // Deleting the accounts removes their room
  for (const user of users) {
    await api?.delete('/api/users/me', { headers: { Authorization: `Bearer ${user.token}` } });
  }
  await api?.dispose();
  await proxy?.close();
});

test('three participants hear each other, directly and through the relay', async ({ browser }) => {
  const pages = [];
  for (const user of users) {
    const context = await browser.newContext();
    await context.addInitScript(({ token, profile }) => {
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(profile));
    }, { token: user.token, profile: user.profile });
    const page = await context.newPage();
    await page.goto(`/rooms/${roomId}?debug=1`);
    pages.push(page);
  }
  for (const page of pages) {
    await page.waitForFunction(() => Boolean(window.__syncchordRtc), null, { timeout: 30_000 });
  }

  // Connections come up without any microphone: listening needs none
  for (const page of pages) {
    await waitForPeers(page, (peers) => peers.length === 2 && peers.every((peer) => peer.state === 'connected'));
  }

  // The real button for one participant, the hook for the others
  await pages[0].getByRole('button', { name: 'Turn microphone on' }).click();
  await expect(pages[0].getByRole('button', { name: 'Microphone on' })).toBeVisible();
  for (const page of pages.slice(1)) {
    await page.evaluate(() => window.__syncchordRtc.setMicrophone(true));
  }
  for (const page of pages) {
    await page.waitForFunction(() => window.__syncchordRtc.state().micState === 'on', null, { timeout: 15_000 });
  }

  const direct = await Promise.all(pages.map((page) => audioFlows(page)));
  for (const peers of direct) {
    for (const peer of peers) {
      // What this side's encoder was asked for by the tuned description
      expect(peer.sendFormat).toContain('stereo=1');
      expect(peer.sendFormat).toContain('maxaveragebitrate=128000');
      expect(peer.sendFormat).toContain('useinbandfec=1');
    }
  }
  // The list shows the connections
  await expect(pages[0].getByText('audio connected')).toHaveCount(2);

  // Relay only: every connection moves to the TURN relay and audio keeps flowing
  for (const page of pages) {
    await page.evaluate(() => window.__syncchordRtc.setRelayOnly(true));
  }
  for (const page of pages) {
    await waitForPeers(page, (peers) => peers.length === 2
      && peers.every((peer) => peer.state === 'connected' && peer.candidateType === 'relay'));
  }
  const relayed = await Promise.all(pages.map((page) => audioFlows(page)));
  for (const peers of relayed) {
    expect(peers.every((peer) => peer.relay)).toBe(true);
  }
  await expect(pages[0].getByText('Relay only')).toBeVisible();

  const summary = (peers) => peers.map((peer) => `${peer.candidateType} rtt ${peer.rttMs} ms`).join(', ');
  console.log(`Audio mesh: direct [${direct.map(summary).join(' | ')}]; relay only [${relayed.map(summary).join(' | ')}]`);

  // A participant who leaves the room is disconnected from the other two
  await post(`/api/rooms/${roomId}/leave`, users[2]);
  for (const page of pages.slice(0, 2)) {
    await waitForPeers(page, (peers) => peers.length === 1 && peers[0].state === 'connected');
  }
});

/**
 * Waits until the page's connections, as its debug hook reports them, satisfy the check.
 */
async function waitForPeers(page, check) {
  let last = [];
  const describe = () => last.map((peer) => `${peer.state}/${peer.candidateType}`).join(', ');
  await expect.poll(async () => {
    last = await page.evaluate(() => window.__syncchordRtc.peers());
    return check(last);
  }, {
    // Evaluated on failure only: what the connections looked like when time ran out
    message: { toString: () => `connections: [${describe()}]` },
    timeout: CONNECT_TIMEOUT_MS,
    intervals: [500],
  }).toBe(true);
}

/**
 * Asserts the page receives more audio from both others, and sends more to them, over two seconds; returns the
 * connections' statistics.
 */
async function audioFlows(page) {
  let before;
  await expect.poll(async () => {
    before = await page.evaluate(() => window.__syncchordRtc.peers());
    return before.length === 2 && before.every((peer) => peer.bytesReceived > 0 && peer.bytesSent > 0);
  }, { timeout: CONNECT_TIMEOUT_MS, intervals: [500] }).toBe(true);
  await page.waitForTimeout(2_000);
  const after = await page.evaluate(() => window.__syncchordRtc.peers());
  for (const peer of after) {
    const earlier = before.find((candidate) => candidate.userId === peer.userId);
    expect(peer.bytesReceived, `audio received from ${peer.userId}`).toBeGreaterThan(earlier.bytesReceived);
    expect(peer.bytesSent, `audio sent to ${peer.userId}`).toBeGreaterThan(earlier.bytesSent);
  }
  return after;
}

async function register(displayName) {
  const email = `e2e-audio-${randomUUID()}@example.test`;
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
