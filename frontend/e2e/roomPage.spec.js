import { randomUUID } from 'node:crypto';
import { expect, request, test } from '@playwright/test';
import { API_URL, PROXY_PORT } from './e2eConfig.js';
import { startLatencyProxy } from './latencyProxy.js';

/**
 * The room page's lesson flow (KAN-50; UR-1, UR-3, FR-8): a host and a participant each open the room. The host
 * picks a tab and starts the lesson, plays and pauses, goes to a measure, changes the tempo and switches the
 * metronome, with the buttons and with the keyboard, changes the tab and ends the lesson; the participant sees every
 * change without a reload and has no controls that work.
 */

// 32 measures of 4/4
const rawTab = (fret) => ['e', 'B', 'G', 'D', 'A', 'E']
  .map((string) => `${string}|${`-${fret}-${fret}-${fret}-${fret}-|`.repeat(32)}`).join('\n');
const FIRST_TAB = 'Room page first tab';
const SECOND_TAB = 'Room page second tab';

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
  for (const name of ['Room page host', 'Room page participant']) {
    users.push(await register(name));
  }
  const [host, participant] = users;
  for (const [title, tempoBpm, fret] of [[FIRST_TAB, 120, 0], [SECOND_TAB, 90, 2]]) {
    await post('/api/tabs', host, {
      title, artist: 'E2E', tempoBpm, timeSignature: '4/4', isPublic: false, rightsConfirmed: true,
      rawText: rawTab(fret),
    });
  }
  const room = await post('/api/rooms', host, { name: 'Room page check' });
  roomId = room.id;
  await post(`/api/rooms/join/${encodeURIComponent(room.inviteCode)}`, participant);
});

test.afterAll(async () => {
  // Deleting the accounts removes their room, lesson and tabs
  for (const user of users) {
    await api?.delete('/api/users/me', { headers: { Authorization: `Bearer ${user.token}` } });
  }
  await api?.dispose();
  await proxy?.close();
});

test('the host runs a lesson and the participant follows it without a reload', async ({ browser }) => {
  const [hostPage, participantPage] = await Promise.all(users.map(async (user) => {
    const context = await browser.newContext();
    await context.addInitScript(({ token, profile }) => {
      localStorage.setItem('token', token);
      localStorage.setItem('user', JSON.stringify(profile));
    }, { token: user.token, profile: user.profile });
    const page = await context.newPage();
    await page.goto(`/rooms/${roomId}`);
    return page;
  }));
  const both = [hostPage, participantPage];
  const tempo = (page) => page.locator('[data-tempo]');

  await expect(participantPage.getByText('Waiting for the host to start the lesson.')).toBeVisible();

  // Start with a tab picked from the host's own
  await hostPage.getByRole('button', { name: 'Pick a tab and start' }).click();
  const startDialog = hostPage.getByRole('dialog', { name: 'Start the lesson' });
  await startDialog.getByRole('button', { name: new RegExp(FIRST_TAB) }).click();
  await startDialog.getByRole('button', { name: 'Start lesson' }).click();
  await expect(startDialog).toHaveCount(0);

  for (const page of both) {
    await expect(page.getByText('Measure 1 of 32')).toBeVisible({ timeout: 20_000 });
    await expect(tempo(page)).toHaveText('120');
    await expect(page.getByLabel('Lesson time left')).toHaveText(/(40:00|39:\d\d) left/);
  }
  // The participant sees the controls, but they do nothing
  await expect(participantPage.getByRole('button', { name: 'Play' })).toBeDisabled();
  await expect(participantPage.getByRole('button', { name: 'Faster' })).toBeDisabled();
  await expect(participantPage.getByRole('button', { name: 'End lesson' })).toHaveCount(0);
  await expect(participantPage.getByLabel('Go to measure')).toHaveCount(0);

  // Buttons
  await hostPage.getByRole('button', { name: 'Faster' }).click();
  for (const page of both) await expect(tempo(page)).toHaveText('125');
  await hostPage.getByLabel('Go to measure').fill('9');
  await hostPage.getByRole('button', { name: 'Go', exact: true }).click();
  for (const page of both) await expect(page.getByText('Measure 9 of 32')).toBeVisible();
  await hostPage.getByLabel('Go to measure').fill('33');
  await expect(hostPage.getByRole('button', { name: 'Go', exact: true })).toBeDisabled();
  await hostPage.getByLabel('Go to measure').fill('');
  await hostPage.getByRole('button', { name: 'Metronome off' }).click();
  await expect(hostPage.getByRole('button', { name: 'Metronome on' })).toBeVisible();
  await expect(participantPage.getByText('Metronome on')).toBeVisible();

  // Keyboard, right after clicking a button (which must not keep the keys): arrows, then space plays and pauses
  await hostPage.keyboard.press('ArrowDown');
  for (const page of both) await expect(tempo(page)).toHaveText('120');
  await hostPage.keyboard.press('ArrowRight');
  for (const page of both) await expect(page.getByText('Measure 10 of 32')).toBeVisible();
  await hostPage.keyboard.press('Space');
  for (const page of both) await expect(page.getByText('Playing', { exact: true })).toBeVisible();
  await expect(participantPage.getByText(/^Measure (1[1-9]|2\d) of 32$/)).toBeVisible({ timeout: 15_000 });
  await hostPage.keyboard.press('Space');
  for (const page of both) await expect(page.getByText('Paused', { exact: true })).toBeVisible();

  // Another tab starts over, stopped, at its own tempo
  await hostPage.getByRole('button', { name: 'Change tab' }).click();
  const changeDialog = hostPage.getByRole('dialog', { name: 'Change the tab' });
  await changeDialog.getByRole('button', { name: new RegExp(SECOND_TAB) }).click();
  await changeDialog.getByRole('button', { name: 'Show this tab' }).click();
  for (const page of both) {
    await expect(tempo(page)).toHaveText('90');
    await expect(page.getByText('Measure 1 of 32')).toBeVisible();
    await expect(page.getByText('Stopped', { exact: true })).toBeVisible();
  }

  // Ending the lesson leaves the room open for the next one
  await hostPage.getByRole('button', { name: 'End lesson' }).click();
  await hostPage.getByRole('dialog', { name: 'End the lesson?' }).getByRole('button', { name: 'End lesson' }).click();
  await expect(participantPage.getByText('The host ended the lesson.')).toBeVisible();
  await expect(participantPage.getByText('Waiting for the host to start the lesson.')).toBeVisible();
  await expect(hostPage.getByRole('button', { name: 'Pick a tab and start' })).toBeVisible();
});

async function register(displayName) {
  const email = `e2e-room-${randomUUID()}@example.test`;
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
