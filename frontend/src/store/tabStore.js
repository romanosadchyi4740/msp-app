import { create } from 'zustand';
import axiosClient from '../api/axiosClient';

const SEARCH_PAGE_SIZE = 20;

/**
 * The user's tabs and the public tab search (`/api/tabs`). Request errors are shown by axiosClient; actions that
 * change data return the result or `null` on failure.
 */
const useTabStore = create((set, get) => ({
  myTabs: [],
  myTabsLoading: false,

  search: { query: '', results: [], pageNo: 0, totalPages: 0, totalElements: 0, loading: false },

  fetchMyTabs: async () => {
    set({ myTabsLoading: true });
    try {
      const response = await axiosClient.get('/api/tabs/my');
      set({ myTabs: response.data });
    } catch (error) {
      console.error('Failed to fetch tabs:', error);
    } finally {
      set({ myTabsLoading: false });
    }
  },

  searchTabs: async (query, pageNo = 0) => {
    set({ search: { ...get().search, query, pageNo, loading: true } });
    try {
      const response = await axiosClient.get('/api/tabs/search', {
        params: { q: query, pageNo, pageSize: SEARCH_PAGE_SIZE },
      });
      const page = response.data;
      // A newer search may have started meanwhile
      if (get().search.query !== query || get().search.pageNo !== pageNo) return;
      set({
        search: {
          query,
          pageNo,
          results: page.content,
          totalPages: page.totalPages,
          totalElements: page.totalElements,
          loading: false,
        },
      });
    } catch (error) {
      console.error('Failed to search tabs:', error);
      if (get().search.query === query && get().search.pageNo === pageNo) {
        set({ search: { ...get().search, loading: false } });
      }
    }
  },

  /**
   * Throws the request error without a toast; the tab page shows it.
   */
  fetchTab: async (id) => {
    const response = await axiosClient.get(`/api/tabs/${id}`, { suppressErrorToast: true });
    return response.data;
  },

  fetchFileUrl: async (id) => {
    const response = await axiosClient.get(`/api/tabs/${id}/url`);
    return response.data;
  },

  /**
   * The alphaTex of ASCII text for the editor preview; throws the request error so the editor can show it.
   */
  previewAscii: async ({ rawText, title, artist, tempoBpm, timeSignature }) => {
    const response = await axiosClient.post(
      '/api/tabs/preview',
      { rawText, title, artist, tempoBpm, timeSignature },
      { suppressErrorToast: true },
    );
    return response.data;
  },

  createAsciiTab: async (tab) => {
    try {
      const response = await axiosClient.post('/api/tabs', tab);
      set({ myTabs: [response.data, ...get().myTabs] });
      return response.data;
    } catch (error) {
      console.error('Failed to create tab:', error);
      return null;
    }
  },

  uploadGuitarProTab: async (file, { title, artist, tempoBpm, timeSignature, isPublic, rightsConfirmed }, onProgress) => {
    const formData = new FormData();
    formData.append('file', file);
    formData.append('title', title);
    if (artist) formData.append('artist', artist);
    if (tempoBpm) formData.append('tempoBpm', tempoBpm);
    if (timeSignature) formData.append('timeSignature', timeSignature);
    formData.append('isPublic', isPublic);
    formData.append('rightsConfirmed', rightsConfirmed);
    try {
      const response = await axiosClient.post('/api/tabs/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        onUploadProgress: (event) => onProgress?.(Math.round((event.loaded * 100) / (event.total || 1))),
      });
      set({ myTabs: [response.data, ...get().myTabs] });
      return response.data;
    } catch (error) {
      console.error('Failed to upload tab:', error);
      return null;
    }
  },

  updateTab: async (id, changes) => {
    try {
      const response = await axiosClient.put(`/api/tabs/${id}`, changes);
      set({ myTabs: get().myTabs.map((tab) => (tab.id === id ? { ...tab, ...response.data, rawText: null } : tab)) });
      return response.data;
    } catch (error) {
      console.error('Failed to update tab:', error);
      return null;
    }
  },

  deleteTab: async (id) => {
    try {
      await axiosClient.delete(`/api/tabs/${id}`);
      set({ myTabs: get().myTabs.filter((tab) => tab.id !== id) });
      return true;
    } catch (error) {
      console.error('Failed to delete tab:', error);
      return false;
    }
  },
}));

export default useTabStore;
