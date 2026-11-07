import socketClient from '../api/socketClient';
import { addEstimate, averageOffset, bestSample, sampleOf } from './clockMath';

/**
 * NTP-style sync of the local clock to the server's (KAN-41), for the sync engine and the metronome, which compute
 * positions from the server's anchor times. A round sends SAMPLES_PER_ROUND requests to /app/time one after
 * another, keeps the answer with the smallest round trip and adds it to a moving average. Rounds run on every
 * (re)connect, every RESAMPLE_INTERVAL_MS and when the browser tab comes back into view, since a hidden tab's
 * timers are throttled and a sleeping laptop's clock may have moved.
 *
 * Whoever needs the server clock calls startClockSync() and, when done, stopClockSync(); the sync runs while at
 * least one caller needs it.
 */

const SAMPLES_PER_ROUND = 8;
const RESAMPLE_INTERVAL_MS = 30_000;
const SAMPLE_TIMEOUT_MS = 2_000;

let users = 0;
let history = [];
let offset = 0;
let rtt = null;
let roundRunning = false;
// Bumped by every new round and by stopping; a round whose number is no longer current ends at its next step
let round = 0;
let pending = null;
let timeSubscription = null;
let stopListening = null;
let resampleTimer = null;
const listeners = new Set();

/**
 * The server clock now, in epoch milliseconds. Before the first round it is the local clock.
 */
export function serverNow() {
  return Date.now() + offset;
}

/**
 * The current estimate: the offset to add to Date.now(), the round trip of the best sample, and whether a round has
 * finished yet.
 */
export function getClockSync() {
  return { offset, rtt, synced: history.length > 0 };
}

/**
 * Calls the listener with getClockSync() after every round; returns the function that removes it.
 */
export function onClockSync(listener) {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function startClockSync() {
  users += 1;
  if (users > 1) {
    return;
  }
  stopListening = socketClient.onConnect(() => {
    // A new connection has no subscriptions; the old one went with the previous connection
    timeSubscription = socketClient.subscribe('/user/queue/time', receive);
    // A round still waiting on the old connection would only wait out its timeouts: start afresh
    runRound(true);
  });
  resampleTimer = setInterval(resample, RESAMPLE_INTERVAL_MS);
  document.addEventListener('visibilitychange', resampleWhenVisible);
  window.addEventListener('focus', resample);
}

export function stopClockSync() {
  if (users === 0) {
    return;
  }
  users -= 1;
  if (users > 0) {
    return;
  }
  stopListening?.();
  stopListening = null;
  timeSubscription?.unsubscribe();
  timeSubscription = null;
  clearInterval(resampleTimer);
  resampleTimer = null;
  document.removeEventListener('visibilitychange', resampleWhenVisible);
  window.removeEventListener('focus', resample);
  round += 1;
  roundRunning = false;
  pending?.resolve(null);
}

function resample() {
  runRound(false);
}

function resampleWhenVisible() {
  if (document.visibilityState === 'visible') {
    resample();
  }
}

/**
 * Runs a round unless one is running already; with restart, ends the running one and starts anew.
 */
async function runRound(restart) {
  if ((roundRunning && !restart) || !timeSubscription || !socketClient.isConnected()) {
    return;
  }
  round += 1;
  const current = round;
  const isCurrent = () => current === round && users > 0;
  roundRunning = true;
  // Ends the wait of the round this one replaces at once
  pending?.resolve(null);
  try {
    const samples = [];
    for (let i = 0; i < SAMPLES_PER_ROUND && isCurrent(); i += 1) {
      const sample = await requestSample();
      if (sample) {
        samples.push(sample);
      }
    }
    const best = bestSample(samples);
    if (best && isCurrent()) {
      history = addEstimate(history, best);
      offset = Math.round(averageOffset(history));
      rtt = best.rtt;
      console.debug(`Clock sync: offset ${offset} ms, best round trip ${rtt} ms`);
      listeners.forEach((listener) => listener(getClockSync()));
    }
  } finally {
    if (current === round) {
      roundRunning = false;
    }
  }
}

/**
 * One request; resolves with its sample, or null if no answer came in time. Requests go one at a time, so an
 * answer belongs to the pending request exactly when it echoes its clientTime.
 */
function requestSample() {
  return new Promise((resolve) => {
    const t0 = Date.now();
    const timeout = setTimeout(() => settle(null), SAMPLE_TIMEOUT_MS);
    function settle(sample) {
      clearTimeout(timeout);
      pending = null;
      resolve(sample);
    }
    pending = { clientTime: t0, resolve: settle };
    if (!socketClient.sendMessage('/time', { clientTime: t0 })) {
      settle(null);
    }
  });
}

function receive(answer) {
  const t1 = Date.now();
  if (pending && answer.clientTime === pending.clientTime) {
    pending.resolve(sampleOf(pending.clientTime, answer.serverTime, t1));
  }
}
