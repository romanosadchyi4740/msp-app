import { create } from 'zustand';
import axiosClient from '../api/axiosClient';

const usePlaylistStore = create((set, get) => ({
  myPlaylists: [],
  loading: false,

  playlistModal: {
    isOpen: false,
    track: null
  },

  openPlaylistModal: (track) => set({ playlistModal: { isOpen: true, track } }),
  closePlaylistModal: () => set({ playlistModal: { isOpen: false, track: null } }),

  fetchMyPlaylists: async () => {
    set({ loading: true });
    try {
      const response = await axiosClient.get('/api/playlists/me');
      set({ myPlaylists: response.data, loading: false });
    } catch (error) {
      console.error('Failed to fetch playlists:', error);
      set({ loading: false });
    }
  },

  addTrackToPlaylist: async (playlistId, trackId) => {
    try {
      await axiosClient.post(`/api/playlists/${playlistId}/tracks/${trackId}`);
      return true;
    } catch (error) {
      console.error('Failed to add track to playlist:', error);
      return false;
    }
  },
  
  createPlaylist: async (title, description, isPublic) => {
    try {
      const res = await axiosClient.post('/api/playlists', { title, description, isPublic });
      set({ myPlaylists: [...get().myPlaylists, res.data] });
      return true;
    } catch (error) {
      console.error('Failed to create playlist:', error);
      return false;
    }
  }
}));

export default usePlaylistStore;