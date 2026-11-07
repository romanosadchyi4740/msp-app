import { mkdirSync, writeFileSync } from 'node:fs';
import { randomUUID } from 'node:crypto';
import { expect, request, test } from '@playwright/test';
import { API_URL, PROXY_PORT } from './e2eConfig.js';
import { startLatencyProxy } from './latencyProxy.js';
import { driftReport } from './driftStats.js';

/**
 * The headline effect E1.1 (KAN-44, NFR-1): a host and two participants, each in their own browser, see the same
 * position in the tab within 150 ms. Their STOMP connections run through a proxy adding 30-80 ms each way, so clock
 * sync and state delivery see a real network's latency and jitter. The host plays, seeks and changes tempo; every
 * client records the position it computes (window.__syncchord, the room page's ?debug=1 hook) every 20 ms. The
 * test fails if the 95th percentile of the drift between them is above 150 ms. Results go to e2e/results/.
 */

const MAX_P95_DRIFT_MS = 150;
const SAMPLE_INTERVAL_MS = 20;
// 32 measures of 4/4: long enough to play through the whole script
const RAW_TAB = ['e', 'B', 'G', 'D', 'A', 'E'].map((string) => `${string}|${'-0-2-3-|'.repeat(32)}`).join('\n');

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

  for (const name of ['Drift host', 'Drift participant 1', 'Drift participant 2']) {
    users.push(await register(name));
  }
  const [host, ...participants] = users;
  const tab = await post('/api/tabs', host, {
    title: 'Drift check', artist: 'E2E', tempoBpm: 120, timeSignature: '4/4', isPublic: true,
    rightsConfirmed: true, rawText: RAW_TAB,
  });
  const room = await post('/api/rooms', host, { name: 'Drift check' });
  roomId = room.id;
  for (const participant of participants) {
    await post(`/api/rooms/join/${encodeURIComponent(room.inviteCode)}`, participant);
  }
  await post(`/api/rooms/${roomId}/sessions`, host, { tabId: tab.id });
});

test.afterAll(async () => {
  // Deleting the accounts removes their room, lesson and tab
  for (const user of users) {
    await api?.delete('/api/users/me', { headers: { Authorization: `Bearer ${user.token}` } });
  }
  await api?.dispose();
  await proxy?.close();
});

test('participants see the same position within 150 ms (p95)', async ({ browser }, testInfo) => {
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
    await page.waitForFunction(() => window.__syncchord?.ready(), null, { timeout: 30_000 });
  }
  const [hostPage] = pages;

  await Promise.all(pages.map((page) => page.evaluate((interval) => window.__syncchord.startSampling(interval),
    SAMPLE_INTERVAL_MS)));
  const commandTimes = [];
  const command = async (type, argument = null, thenWaitMs = 0) => {
    commandTimes.push(Date.now());
    await hostPage.evaluate(([commandType, commandArgument]) => window.__syncchord.send(commandType, commandArgument),
      [type, argument]);
    await hostPage.waitForTimeout(thenWaitMs);
  };
  // PLAY counts in one measure (2 s at 120 BPM) after a 250 ms lead
  await command('PLAY', null, 8_000);
  await command('SEEK', 9, 4_000);
  await command('SET_TEMPO', 90, 5_000);
  await command('SET_TEMPO', 150, 4_000);
  await command('PAUSE', null, 1_000);
  const samples = await Promise.all(pages.map((page) => page.evaluate(() => window.__syncchord.takeSamples())));

  const samplesByClient = { host: samples[0], participant1: samples[1], participant2: samples[2] };
  const report = driftReport(samplesByClient, { commandTimes });
  const clockSync = await Promise.all(pages.map((page) => page.evaluate(() => {
    const { offset, rtt } = window.__syncchord.now();
    return { offset, rtt };
  })));
  const result = {
    test: 'sync drift between participants (NFR-1)',
    date: new Date().toISOString(),
    latency: '30-80 ms each way on STOMP, random per chunk, order kept',
    script: ['PLAY (2 s count-in)', 'SEEK 9', 'SET_TEMPO 90', 'SET_TEMPO 150', 'PAUSE'],
    samplesPerClient: Object.fromEntries(Object.entries(samplesByClient).map(([client, list]) => [client, list.length])),
    clockSync: Object.fromEntries(Object.keys(samplesByClient).map((client, index) => [client, clockSync[index]])),
    driftMs: report,
    limitP95Ms: MAX_P95_DRIFT_MS,
  };

  mkdirSync('e2e/results', { recursive: true });
  const file = `e2e/results/sync-drift-${result.date.replaceAll(':', '-')}.json`;
  writeFileSync(file, JSON.stringify(result, null, 2));
  await testInfo.attach('sync-drift', { body: JSON.stringify(result, null, 2), contentType: 'application/json' });
  console.log(`Sync drift (ms) over ${report.all.points} points while playing: p50 ${report.all.p50}, `
    + `p95 ${report.all.p95}, max ${report.all.max}; settled (${report.settleMs} ms after commands left out): `
    + `p50 ${report.steady.p50}, p95 ${report.steady.p95}, max ${report.steady.max}. Written to ${file}`);

  expect(report.all.points).toBeGreaterThan(500);
  expect(report.all.p95).toBeLessThanOrEqual(MAX_P95_DRIFT_MS);
});

async function register(displayName) {
  const email = `e2e-drift-${randomUUID()}@example.test`;
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
