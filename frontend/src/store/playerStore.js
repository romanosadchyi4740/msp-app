import { create } from 'zustand';
import axiosClient from '../api/axiosClient';

const usePlayerStore = create((set, get) => ({
  currentTrack: null,
  isPlaying: false,
  volume: 0.5,
  audioUrl: null,

  playTrack: async (track) => {
    // If it's the same track and it's already playing, do nothing
    // If it's the same track and it's paused, toggle play
    if (get().currentTrack?.id === track.id) {
      set({ isPlaying: true });
      return;
    }

    try {
      // Fetch presigned URL from backend
      const response = await axiosClient.get(`/api/tracks/${track.id}/url`);
      const url = response.data;

      set({ 
        currentTrack: track, 
        audioUrl: url,
        isPlaying: true 
      });
    } catch (error) {
      console.error('Failed to get track URL:', error);
    }
  },

  togglePlayPause: () => {
    if (!get().currentTrack) return;
    set((state) => ({ isPlaying: !state.isPlaying }));
  },

  setVolume: (volume) => set({ volume }),
  
  setIsPlaying: (isPlaying) => set({ isPlaying }),
}));

export default usePlayerStore;
