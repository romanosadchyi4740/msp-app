/**
 * The drift between participants from their recorded samples (KAN-44). Each sample is what one client computed at
 * one moment: `{ wallTime, quarterBeats, version, status, tempoBpm }`, `quarterBeats` from the start of the tab.
 * All clients run on one machine, so their wall clocks agree and can be compared directly; what differs is what
 * each computed from its own clock sync and the states it received.
 */

/**
 * A client's position at a wall time: interpolated between the samples around it when both belong to the same
 * state (positions move linearly within a state), otherwise the nearer one. Null outside the samples.
 */
export function positionAtWallTime(samples, wallTime) {
  let low = 0;
  let high = samples.length - 1;
  if (high < 0 || wallTime < samples[0].wallTime || wallTime > samples[high].wallTime) return null;
  while (high - low > 1) {
    const middle = (low + high) >> 1;
    if (samples[middle].wallTime <= wallTime) low = middle;
    else high = middle;
  }
  const before = samples[low];
  const after = samples[high];
  if (before.quarterBeats === null || after.quarterBeats === null) return null;
  if (after.wallTime === before.wallTime) return before.quarterBeats;
  if (before.version !== after.version || before.status !== after.status) {
    return wallTime - before.wallTime <= after.wallTime - wallTime ? before.quarterBeats : after.quarterBeats;
  }
  const fraction = (wallTime - before.wallTime) / (after.wallTime - before.wallTime);
  return before.quarterBeats + (after.quarterBeats - before.quarterBeats) * fraction;
}

/**
 * The p-th percentile (0-100) of numbers, nearest rank.
 */
export function percentile(values, p) {
  if (values.length === 0) return null;
  const sorted = [...values].sort((a, b) => a - b);
  const rank = Math.min(sorted.length - 1, Math.max(0, Math.ceil((p / 100) * sorted.length) - 1));
  return sorted[rank];
}

/**
 * The drift between clients every `stepMs` while playback runs: at each moment the largest difference between any
 * two clients' positions, in milliseconds at the tempo then (the host's). Moments in a count-in are left out, as
 * every client shows the stored position then and would only flatter the figures. `commandTimes` are the wall times
 * of the host's commands; `settleMs` after each is reported separately, as the new state is still on its way then.
 */
export function driftReport(samplesByClient, { stepMs = 10, commandTimes = [], settleMs = 300 } = {}) {
  const clients = Object.keys(samplesByClient);
  const host = samplesByClient[clients[0]];
  const start = Math.max(...clients.map((client) => samplesByClient[client][0]?.wallTime ?? Infinity));
  const end = Math.min(...clients.map((client) => samplesByClient[client].at(-1)?.wallTime ?? -Infinity));
  const all = [];
  const steady = [];
  for (let wallTime = start; wallTime <= end; wallTime += stepMs) {
    const hostSample = latestSample(host, wallTime);
    if (!hostSample || hostSample.status !== 'PLAYING' || hostSample.serverTime < hostSample.anchorTime) continue;
    const positions = clients.map((client) => positionAtWallTime(samplesByClient[client], wallTime));
    if (positions.some((position) => position === null)) continue;
    const spread = Math.max(...positions) - Math.min(...positions);
    const driftMs = (spread * 60000) / hostSample.tempoBpm;
    all.push(driftMs);
    if (!commandTimes.some((time) => wallTime >= time && wallTime < time + settleMs)) steady.push(driftMs);
  }
  return { clients: clients.length, stepMs, all: summary(all), steady: summary(steady), settleMs };
}

function summary(values) {
  return {
    points: values.length,
    p50: round(percentile(values, 50)),
    p95: round(percentile(values, 95)),
    max: round(values.length ? Math.max(...values) : null),
  };
}

function latestSample(samples, wallTime) {
  let found = null;
  for (const sample of samples) {
    if (sample.wallTime > wallTime) break;
    found = sample;
  }
  return found;
}

function round(value) {
  return value === null ? null : Math.round(value * 10) / 10;
}
