import { randomUUID } from 'node:crypto';
import { expect, request, test } from '@playwright/test';
import { API_URL, PROXY_PORT } from './e2eConfig.js';
import { startLatencyProxy } from './latencyProxy.js';

/**
 * The device check before a room (KAN-49, UR-2, FR-6), in Chrome with its fake microphone (a tone that beeps):
 * the way from an invite to the check and back, a passing check with a moving level meter, and the two ways it can
 * fail (a browser without WebRTC, a refused microphone), each with instructions and a working "Check again".
 *
 * Needs the backend. The missing WebRTC and the refused microphone are simulated in the page, since the installed
 * Chrome has both.
 */

let proxy;
let api;
const users = [];
let inviteCode;

test.beforeAll(async () => {
  proxy = await startLatencyProxy({
    port: PROXY_PORT,
    targetHost: new URL(API_URL).hostname,
    targetPort: Number(new URL(API_URL).port || 80),
  });
  api = await request.newContext({ baseURL: API_URL });
  for (const name of ['Check host', 'Check guest']) {
    users.push(await register(name));
  }
  const response = await api.post('/api/rooms', {
    headers: { Authorization: `Bearer ${users[0].token}` }, data: { name: 'Device check' },
  });
  expect(response.ok()).toBeTruthy();
  inviteCode = (await response.json()).inviteCode;
});

test.afterAll(async () => {
  for (const user of users) {
    await api?.delete('/api/users/me', { headers: { Authorization: `Bearer ${user.token}` } });
  }
  await api?.dispose();
  await proxy?.close();
});

async function openAs(browser, user, initScript) {
  const context = await browser.newContext();
  await context.addInitScript(({ token, profile }) => {
    localStorage.setItem('token', token);
    localStorage.setItem('user', JSON.stringify(profile));
  }, { token: user.token, profile: user.profile });
  if (initScript) await context.addInitScript(initScript);
  return context.newPage();
}

/** The microphone starts by itself when the browser says it is already allowed; otherwise the button asks. */
async function allowMicrophone(page) {
  const meter = page.getByRole('img', { name: 'Microphone level' });
  const button = page.getByRole('button', { name: 'Allow microphone' });
  await expect(meter.or(button)).toBeVisible();
  // The button is disabled, then gone, while the microphone is starting by itself: then there is nothing to click
  await button.click({ timeout: 2_000 }).catch(() => {});
  await expect(meter).toBeVisible();
}

test('from the invite to the check, which passes, and back', async ({ browser }) => {
  const page = await openAs(browser, users[1]);
  await page.goto(`/join/${inviteCode}`);
  await page.getByRole('link', { name: 'Check microphone and sound first' }).click();
  await expect(page.getByRole('heading', { name: 'Device check' })).toBeVisible();

  // Every required capability is there
  await expect(page.locator('[data-check][data-ok="true"]')).toHaveCount(4);
  await expect(page.locator('[data-check][data-ok="false"]')).toHaveCount(0);

  await allowMicrophone(page);
  // The fake microphone's tone moves the meter
  await expect(page.locator('[data-heard="true"]')).toBeVisible({ timeout: 15_000 });
  await expect.poll(async () => Number(await page.locator('[data-level]').getAttribute('data-level')),
    { timeout: 15_000 }).toBeGreaterThan(0.25);

  // The devices are offered once the microphone is allowed, and a choice is remembered
  const microphones = page.getByLabel('Microphone', { exact: true });
  await expect.poll(() => microphones.locator('option').count()).toBeGreaterThan(1);
  const chosen = await microphones.locator('option').nth(1).getAttribute('value');
  await microphones.selectOption(chosen);
  await expect(page.getByRole('img', { name: 'Microphone level' })).toBeVisible();
  expect(await page.evaluate(() => JSON.parse(localStorage.getItem('audio-settings')).state.inputDeviceId))
    .toBe(chosen);

  // Chrome can choose the output, and offers the fake ones
  await expect.poll(() => page.getByLabel('Headphones or speakers').locator('option').count()).toBeGreaterThan(1);

  await page.getByRole('button', { name: 'Play test sound' }).click();
  await expect(page.getByText('You should have heard two short tones.')).toBeVisible();
  await expect(page.getByText('Plug in headphones.')).toBeVisible();

  await expect(page.locator('[data-ready="true"]')).toBeVisible();
  await page.getByRole('link', { name: 'Continue', exact: true }).click();
  await expect(page).toHaveURL(new RegExp(`/join/${inviteCode}$`));
  await expect(page.getByRole('button', { name: 'Join room' })).toBeVisible();
});

test('a browser without WebRTC gets instructions, and "Check again" passes once it is there', async ({ browser }) => {
  const page = await openAs(browser, users[1], () => {
    window.__realPeerConnection = window.RTCPeerConnection;
    window.RTCPeerConnection = undefined;
  });
  await page.goto(`/join/${inviteCode}`);
  await expect(page.getByRole('alert')).toContainText('cannot take part in the room');

  await page.goto('/device-check');
  await expect(page.locator('[data-check="webrtc"][data-ok="false"]')).toContainText('Use a current version');
  await expect(page.getByRole('alert')).toContainText('Follow the steps above, then check again');
  await expect(page.getByRole('button', { name: 'Allow microphone' })).toHaveCount(0);
  await expect(page.getByRole('img', { name: 'Microphone level' })).toHaveCount(0);
  await expect(page.locator('[data-ready="false"]')).toBeVisible();

  await page.evaluate(() => {
    window.RTCPeerConnection = window.__realPeerConnection;
  });
  await page.getByRole('button', { name: 'Check again' }).click();
  await expect(page.locator('[data-check="webrtc"][data-ok="true"]')).toBeVisible();
  await expect(page.getByRole('img', { name: 'Microphone level' })).toBeVisible();
});

test('a refused microphone gets a hint, and "Check again" passes once it is allowed', async ({ browser }) => {
  const page = await openAs(browser, users[1], () => {
    const real = navigator.mediaDevices.getUserMedia.bind(navigator.mediaDevices);
    window.__refuseMicrophone = true;
    navigator.mediaDevices.getUserMedia = (constraints) => (window.__refuseMicrophone
      ? Promise.reject(new DOMException('Permission denied', 'NotAllowedError'))
      : real(constraints));
  });
  await page.goto('/device-check');

  const refused = page.getByRole('alert');
  await expect(refused.or(page.getByRole('button', { name: 'Allow microphone' })).first()).toBeVisible();
  if (!(await refused.isVisible())) {
    await page.getByRole('button', { name: 'Allow microphone' }).click();
  }
  await expect(refused).toContainText('The browser did not allow the microphone.');
  await expect(refused).toContainText('address bar');
  await expect(page.locator('[data-ready="false"]')).toBeVisible();

  await page.evaluate(() => {
    window.__refuseMicrophone = false;
  });
  await page.getByRole('button', { name: 'Check again' }).click();
  await expect(page.getByRole('img', { name: 'Microphone level' })).toBeVisible();
  await expect(page.locator('[data-ready="true"]')).toBeVisible();
});

async function register(displayName) {
  const email = `e2e-check-${randomUUID()}@example.test`;
  const response = await api.post('/auth/register', {
    data: { email, password: `E2e-${randomUUID()}`, displayName },
  });
  expect(response.ok(), `registering ${displayName}`).toBeTruthy();
  const { accessToken, ...profile } = await response.json();
  return { token: accessToken, profile };
}
