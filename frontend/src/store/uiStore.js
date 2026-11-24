import { create } from 'zustand';
import { persist } from 'zustand/middleware';

const useUiStore = create(
  persist(
    (set) => ({
      theme: 'dark', // default to dark theme
      toggleTheme: () => set((state) => ({ theme: state.theme === 'dark' ? 'light' : 'dark' })),
      setTheme: (theme) => set({ theme }),
      // Which room roles' first-visit hints the user has dismissed (KAN-53): a participant still gets the host's
      // hints when they open their own room
      roomHintsSeen: { host: false, participant: false },
      dismissRoomHints: (role) => set((state) => ({ roomHintsSeen: { ...state.roomHintsSeen, [role]: true } })),
    }),
    {
      name: 'ui-storage', // name of the item in the storage (must be unique)
    }
  )
);

export default useUiStore;
