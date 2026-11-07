import socketClient from '../api/socketClient';
import usePlaybackStore, { measureTableOf } from '../store/playbackStore';
import { getClockSync, serverNow } from './clockSync';
import { quarterBeatsAt } from './playbackMath';

/**
 * The room page's debug hook (KAN-44), installed only with `?debug=1`: `window.__syncchord`, for the debug overlay
 * and the end-to-end drift test, which compare what each participant computes at the same moment.
 *
 * - `ready()`: whether the clock is synced and the playback state has arrived.
 * - `now()`: this client's view, `{ wallTime, serverTime, quarterBeats, version, status, tempoBpm, offset, rtt }`;
 *   `quarterBeats` counts from the start of the tab, so it can be compared across clients whatever the measures.
 * - `startSampling(intervalMs)` / `takeSamples()`: records now() every interval, and hands over what was recorded.
 * - `send(type, argument)`: sends a playback command to the room, as the host's controls will (KAN-50); the server
 *   refuses it from anyone but the host.
 *
 * Returns the function that removes it.
 */
export function installSyncDebugHook(roomId) {
  let samples = [];
  let sampler = null;

  const now = () => {
    const state = usePlaybackStore.getState();
    const { playback } = state;
    const serverTime = serverNow();
    const { offset, rtt } = getClockSync();
    return {
      wallTime: Date.now(),
      serverTime,
      quarterBeats: playback ? quarterBeatsAt(playback, serverTime, measureTableOf(state)) : null,
      version: playback?.version ?? null,
      sessionId: playback?.sessionId ?? null,
      status: playback?.status ?? null,
      tempoBpm: playback?.tempoBpm ?? null,
      offset,
      rtt,
    };
  };

  const hook = {
    ready: () => getClockSync().synced && Boolean(usePlaybackStore.getState().playback),
    now,
    startSampling: (intervalMs = 20) => {
      clearInterval(sampler);
      samples = [];
      sampler = setInterval(() => samples.push(now()), intervalMs);
    },
    takeSamples: () => {
      clearInterval(sampler);
      sampler = null;
      const taken = samples;
      samples = [];
      return taken;
    },
    send: (type, argument = null) => socketClient.sendMessage(`/rooms/${roomId}/command`,
      { type, argument: argument === null ? null : String(argument), clientTime: Date.now() }),
  };
  window.__syncchord = hook;

  return () => {
    clearInterval(sampler);
    if (window.__syncchord === hook) delete window.__syncchord;
  };
}
