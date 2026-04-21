import { create } from 'zustand';
import axiosClient from '../api/axiosClient';

const useLikeStore = create((set, get) => ({
  likedTrackIds: {},
  initialized: false,

  fetchLikedTracks: async () => {
    try {
      const response = await axiosClient.get('/api/tracks/liked');
      const idMap = {};
      response.data.forEach(track => {
        idMap[track.id] = true;
      });
      set({ likedTrackIds: idMap, initialized: true });
    } catch (error) {
      console.error('Failed to fetch liked tracks:', error);
    }
  },

  toggleLike: async (trackId) => {
    const { likedTrackIds } = get();
    const isLiked = !!likedTrackIds[trackId];
    
    // Optimistic UI update
    set({
      likedTrackIds: {
        ...likedTrackIds,
        [trackId]: !isLiked
      }
    });

    try {
      if (isLiked) {
        await axiosClient.delete(`/api/tracks/${trackId}/like`);
      } else {
        await axiosClient.post(`/api/tracks/${trackId}/like`);
      }
    } catch (error) {
      console.error('Failed to toggle like:', error);
      // Revert on failure
      set({
        likedTrackIds: {
          ...likedTrackIds,
          [trackId]: isLiked
        }
      });
    }
  },
}));

export default useLikeStore;