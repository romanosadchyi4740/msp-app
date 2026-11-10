import useRtcStore from '../store/rtcStore';
import { disableMicrophone, enableMicrophone, getRtcStats } from './rtcMesh';

/**
 * The room page's audio debug hook (KAN-47), installed only with `?debug=1`: `window.__syncchordRtc`, for the
 * end-to-end test, which checks that the participants' browsers really exchange audio.
 *
 * - `peers()`: a promise of every connection as `{ userId, state, candidateType, relay, rttMs, jitterMs,
 *   packetsReceived, packetsLost, bytesReceived, bytesSent, sendFormat }`.
 * - `state()`: `{ micState, relayOnly, soundBlocked }`.
 * - `setMicrophone(on)`, `setRelayOnly(on)`: what the audio panel's button and the setting do.
 *
 * Returns the function that removes it.
 */
export function installRtcDebugHook() {
  const hook = {
    peers: () => getRtcStats(),
    state: () => {
      const { micState, relayOnly, soundBlocked } = useRtcStore.getState();
      return { micState, relayOnly, soundBlocked };
    },
    setMicrophone: (on) => (on ? enableMicrophone() : disableMicrophone()),
    setRelayOnly: (on) => useRtcStore.getState().setRelayOnly(on),
  };
  window.__syncchordRtc = hook;
  return () => {
    if (window.__syncchordRtc === hook) delete window.__syncchordRtc;
  };
}
