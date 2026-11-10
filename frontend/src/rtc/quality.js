/**
 * How good an audio connection is (KAN-48, FR-13), from the figures of `summarizeStats`. Pure, so it is tested
 * without a browser.
 */

/** Up to these a connection is 'good': playing together feels immediate. */
export const GOOD = { rttMs: 150, jitterMs: 30, lossPercent: 2 };
/** Above any of these it is 'poor': notes arrive late or drop out. In between it is 'fair'. */
export const POOR = { rttMs: 300, jitterMs: 60, lossPercent: 8 };

/**
 * 'good', 'fair' or 'poor' for a connected peer, by the worst of round trip, jitter and packet loss; figures that
 * are not measured yet (null) do not count. Null while the connection is not up, since there is nothing to rate.
 */
export function qualityOf({ state, rttMs = null, jitterMs = null, lossPercent = null }) {
  if (state !== 'connected') return null;
  const figures = { rttMs, jitterMs, lossPercent };
  const above = (limits) => Object.keys(limits).some((key) => figures[key] !== null && figures[key] > limits[key]);
  if (above(POOR)) return 'poor';
  return above(GOOD) ? 'fair' : 'good';
}

/**
 * The share of audio packets lost between two readings of a connection's counters, in percent (0 to 100); null
 * when no packets were expected in between, or there is no earlier reading. The counters are totals since the
 * connection started, so only their change says how the connection is doing now.
 */
export function lossPercentBetween(previous, current) {
  if (!previous) return null;
  const lost = current.packetsLost - previous.packetsLost;
  const received = current.packetsReceived - previous.packetsReceived;
  // The counters start over with a new connection, and `packetsLost` may go down when late packets arrive
  if (received < 0) return null;
  const expected = Math.max(lost, 0) + received;
  return expected > 0 ? Math.round((Math.max(lost, 0) / expected) * 1000) / 10 : null;
}

/**
 * The average of the round-trip times that were measured, rounded to whole milliseconds; null if none was.
 */
export function averageRtt(rtts) {
  const measured = rtts.filter((rtt) => typeof rtt === 'number' && Number.isFinite(rtt));
  if (measured.length === 0) return null;
  return Math.round(measured.reduce((sum, rtt) => sum + rtt, 0) / measured.length);
}
