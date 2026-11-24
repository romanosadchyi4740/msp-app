import axiosClient from '../api/axiosClient';
import useRtcStore from '../store/rtcStore';
import { averageRtt, lossPercentBetween, qualityOf } from './quality';
import { getRtcStats } from './rtcMesh';

/**
 * Watches the quality of the room's audio connections (KAN-48, FR-13).
 *
 * Every 2 seconds it reads each connection's statistics and puts into the rtc store, per peer, the round trip,
 * jitter, the packet loss since the reading before, whether the audio goes through the TURN relay, and the grade
 * ('good', 'fair', 'poor'; null while not connected) the participant list colours its indicator with.
 *
 * Every 10 seconds it reports this participant's average round trip to the connected peers to the server
 * (`PUT /api/rooms/{roomId}/quality`), which tells the room, so everyone sees who has a slow connection.
 *
 * Timers are fine here, unlike for the metronome: nothing audible depends on when a reading is taken.
 *
 * Returns the function that stops it.
 */
const READ_INTERVAL_MS = 2000;
const READS_PER_REPORT = 5;

export function startQualityMonitor(roomId) {
  let previous = new Map();
  let rtts = [];
  let reads = 0;
  let reading = false;
  let stopped = false;

  const read = async () => {
    // A reading slower than the interval is not overtaken by the next one
    if (reading) return;
    reading = true;
    try {
      const peers = await getRtcStats();
      if (stopped) return;
      const current = new Map();
      for (const peer of peers) {
        const lossPercent = lossPercentBetween(previous.get(peer.userId), peer);
        const { state, rttMs, jitterMs, relay } = peer;
        // A connection closed while its statistics were read must not come back into the store
        if (!useRtcStore.getState().peers[peer.userId]) continue;
        useRtcStore.getState().setPeer(peer.userId, {
          rttMs,
          jitterMs,
          lossPercent,
          relay,
          quality: qualityOf({ state, rttMs, jitterMs, lossPercent }),
        });
        current.set(peer.userId, peer);
        if (state === 'connected' && rttMs !== null) rtts.push(rttMs);
      }
      previous = current;
      reads += 1;
      if (reads % READS_PER_REPORT === 0) {
        const rttMs = averageRtt(rtts);
        rtts = [];
        if (rttMs !== null) {
          // Best effort: the next report comes in 10 seconds anyway
          axiosClient.put(`/api/rooms/${roomId}/quality`, { rttMs }, { suppressErrorToast: true }).catch(() => {});
        }
      }
    } catch (error) {
      console.warn(`Could not read the connection statistics: ${error.name}`);
    } finally {
      reading = false;
    }
  };

  const timer = setInterval(read, READ_INTERVAL_MS);
  return () => {
    stopped = true;
    clearInterval(timer);
  };
}
