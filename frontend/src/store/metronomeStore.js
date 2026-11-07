import { create } from 'zustand';
import { persist } from 'zustand/middleware';

/**
 * This participant's metronome settings (KAN-43). Whether the metronome clicks is the host's choice and part of
 * the shared playback state; how loud it is, is each participant's own, kept in this browser.
 *
 * - `volume`: 0 to 1.
 * - `audioState`: the AudioContext's state ('suspended' until a user gesture allows sound, then 'running'), or null
 *   while no metronome runs. Not persisted.
 */
const useMetronomeStore = create(
  persist(
    (set) => ({
      volume: 0.8,
      audioState: null,

      setVolume: (volume) => set({ volume: Math.min(1, Math.max(0, Number(volume) || 0)) }),
      setAudioState: (audioState) => set({ audioState }),
    }),
    {
      name: 'metronome-settings',
      partialize: (state) => ({ volume: state.volume }),
    },
  ),
);

export default useMetronomeStore;
