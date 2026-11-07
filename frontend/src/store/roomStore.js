import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import axiosClient from '../api/axiosClient';
import useAuthStore from './authStore';

// Planned tabs are kept per user, as several accounts may use one browser; without a user there are none
const plannedKey = (roomId) => {
  const userId = useAuthStore.getState().user?.userId;
  return userId ? `${userId}:${roomId}` : null;
};

/**
 * The tab the current user picked for the room, if any.
 */
export const plannedTabOf = (state, roomId) => state.plannedTabs[plannedKey(roomId)];

/**
 * The user's rooms (`/api/rooms`): the list, creating, joining by invite and leaving. Request errors are shown by
 * axiosClient unless an action says it throws them for the page to show.
 *
 * `plannedTabs` keeps, per user and room (read it with `plannedTabOf`), the tab the host picked when creating the
 * room (`{ id, title, artist }`). Picking a tab does not start the lesson, so the free plan's lesson time does not run
 * while the host waits for the others; the room page starts the lesson with it. It is kept in this browser only.
 */
const useRoomStore = create(
  persist(
    (set, get) => ({
      myRooms: [],
      myRoomsLoading: false,
      plannedTabs: {},

      fetchMyRooms: async () => {
        set({ myRoomsLoading: true });
        try {
          const response = await axiosClient.get('/api/rooms/my');
          set({ myRooms: response.data });
        } catch (error) {
          console.error('Failed to fetch rooms:', error);
        } finally {
          set({ myRoomsLoading: false });
        }
      },

      /**
       * Creates the room and remembers the picked tab, if any; returns the room or `null` on failure.
       */
      createRoom: async (name, tab) => {
        try {
          const response = await axiosClient.post('/api/rooms', { name });
          const room = response.data;
          if (tab) {
            get().setPlannedTab(room.id, tab);
          }
          return room;
        } catch (error) {
          console.error('Failed to create room:', error);
          return null;
        }
      },

      setPlannedTab: (roomId, tab) => {
        const { id, title, artist } = tab;
        if (!plannedKey(roomId)) return;
        set({ plannedTabs: { ...get().plannedTabs, [plannedKey(roomId)]: { id, title, artist } } });
      },

      /**
       * Throws the request error without a toast; the invite page shows it.
       */
      fetchPreview: async (code) => {
        const response = await axiosClient.get(`/api/rooms/invite/${encodeURIComponent(code)}`,
          { suppressErrorToast: true });
        return response.data;
      },

      /**
       * Joins by invite (joining a room one is already in changes nothing). Throws the request error without a
       * toast; the invite page shows it.
       */
      joinRoom: async (code) => {
        const response = await axiosClient.post(`/api/rooms/join/${encodeURIComponent(code)}`, null,
          { suppressErrorToast: true });
        return response.data;
      },

      /**
       * Throws the request error without a toast; the room page shows it.
       */
      fetchRoom: async (id) => {
        const response = await axiosClient.get(`/api/rooms/${id}`, { suppressErrorToast: true });
        return response.data;
      },

      /**
       * The room's active lesson, or `null` if it has none (the API answers 404). Throws other request errors without
       * a toast; the room page shows them.
       */
      fetchCurrentLesson: async (id) => {
        try {
          const response = await axiosClient.get(`/api/rooms/${id}/sessions/current`, { suppressErrorToast: true });
          return response.data;
        } catch (error) {
          if (error.response?.status === 404) return null;
          throw error;
        }
      },

      leaveRoom: async (id) => {
        try {
          await axiosClient.post(`/api/rooms/${id}/leave`);
          const { [plannedKey(id)]: _left, ...plannedTabs } = get().plannedTabs;
          set({ myRooms: get().myRooms.filter((room) => room.id !== id), plannedTabs });
          return true;
        } catch (error) {
          console.error('Failed to leave room:', error);
          return false;
        }
      },
    }),
    {
      name: 'room-storage',
      partialize: (state) => ({ plannedTabs: state.plannedTabs }),
    },
  ),
);

// Signing out (or deleting the account) forgets the rooms and the planned tabs, so the next user of this browser
// sees nothing of them
useAuthStore.subscribe((state, previous) => {
  if (previous.isAuthenticated && !state.isAuthenticated) {
    useRoomStore.setState({ myRooms: [], plannedTabs: {} });
  }
});

export default useRoomStore;
