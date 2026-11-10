import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * The room's audio as this participant sees it (KAN-47); written by the audio mesh (`rtc/rtcMesh.js`).
 *
 * - `relayOnly`: this participant's setting, kept in this browser. Audio then goes only through the TURN relay, so
 *   the other participants never learn this participant's IP address (R15), at the price of a longer route.
 * - `micState`: 'off', 'starting' (the browser is asking for permission), 'on' or 'error'; `micError` says why.
 * - `soundBlocked`: the browser refused to play the others' audio until the user clicks something.
 * - `peers`: per other participant's user id, `{ state }` with the RTCPeerConnection's connection state ('new',
 *   'connecting', 'connected', 'disconnected', 'failed'); a participant without a connection has no entry.
 */
const useRtcStore = create(
  persist(
    (set, get) => ({
      relayOnly: false,
      micState: 'off',
      micError: null,
      soundBlocked: false,
      peers: {},

      setRelayOnly: (relayOnly) => set({ relayOnly: Boolean(relayOnly) }),
      setMic: (micState, micError = null) => set({ micState, micError }),
      setSoundBlocked: (soundBlocked) => set({ soundBlocked }),
      setPeer: (userId, peer) => set({ peers: { ...get().peers, [userId]: { ...get().peers[userId], ...peer } } }),
      removePeer: (userId) => {
        const { [userId]: _removed, ...peers } = get().peers;
        set({ peers });
      },
      resetSession: () => set({ micState: 'off', micError: null, soundBlocked: false, peers: {} }),
    }),
    {
      name: 'audio-settings',
      partialize: (state) => ({ relayOnly: state.relayOnly }),
    },
  ),
);

export default useRtcStore;
