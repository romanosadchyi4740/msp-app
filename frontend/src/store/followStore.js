import { create } from 'zustand';
import axiosClient from '../api/axiosClient';

const useFollowStore = create((set, get) => ({
  followingIds: {},
  initialized: false,

  fetchFollowing: async () => {
    try {
      const response = await axiosClient.get('/api/users/following');
      const idMap = {};
      response.data.forEach(user => {
        idMap[user.id] = true;
      });
      set({ followingIds: idMap, initialized: true });
    } catch (error) {
      console.error('Failed to fetch following users:', error);
    }
  },

  toggleFollow: async (userId) => {
    const { followingIds } = get();
    const isFollowing = !!followingIds[userId];
    
    // Optimistic UI update
    set({
      followingIds: {
        ...followingIds,
        [userId]: !isFollowing
      }
    });

    try {
      if (isFollowing) {
        await axiosClient.delete(`/api/users/${userId}/follow`);
      } else {
        await axiosClient.post(`/api/users/${userId}/follow`);
      }
    } catch (error) {
      console.error('Failed to toggle follow:', error);
      // Revert on failure
      set({
        followingIds: {
          ...followingIds,
          [userId]: isFollowing
        }
      });
    }
  },
}));

export default useFollowStore;