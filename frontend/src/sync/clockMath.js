/**
 * The arithmetic of the NTP-style clock sync (KAN-41), kept free of the socket and the browser so it can be tested
 * with synthetic delays. All times are epoch milliseconds; an offset is what to add to the local clock to get the
 * server clock.
 */

/** How many round estimates the moving average keeps. */
export const HISTORY_SIZE = 4;

/**
 * Slack added to the error bounds before two estimates are taken to disagree, for the millisecond resolution of
 * both clocks and the time the server spends handling the request.
 */
export const JUMP_SLACK_MS = 10;

/**
 * One request and its answer: sent at local time t0, handled at serverTime, answered at local time t1.
 * Assuming the request and the answer took equally long, the server read its clock at local time (t0 + t1) / 2.
 * However the delay is split, the true offset is within rtt / 2 of the estimate.
 */
export function sampleOf(t0, serverTime, t1) {
  return { offset: serverTime - (t0 + t1) / 2, rtt: t1 - t0 };
}

/**
 * The sample of a round with the smallest round trip, which has the smallest error bound; null for no samples.
 */
export function bestSample(samples) {
  let best = null;
  for (const sample of samples) {
    if (best === null || sample.rtt < best.rtt) {
      best = sample;
    }
  }
  return best;
}

/**
 * The offset of the moving average over the history of round estimates; 0 for an empty history.
 */
export function averageOffset(history) {
  if (history.length === 0) {
    return 0;
  }
  return history.reduce((sum, estimate) => sum + estimate.offset, 0) / history.length;
}

/**
 * The history after a new round estimate: appended, keeping the last HISTORY_SIZE. If the estimate cannot agree
 * with the average (their error bounds of rtt / 2 do not overlap), one of the clocks has jumped (a changed system
 * time, a laptop waking up), and averaging it with older estimates would only spread the error over several rounds,
 * so the history restarts from the new estimate.
 */
export function addEstimate(history, estimate) {
  if (history.length > 0) {
    const bestRtt = Math.min(...history.map((previous) => previous.rtt));
    const tolerance = estimate.rtt / 2 + bestRtt / 2 + JUMP_SLACK_MS;
    if (Math.abs(estimate.offset - averageOffset(history)) > tolerance) {
      return [estimate];
    }
  }
  return [...history, estimate].slice(-HISTORY_SIZE);
}
