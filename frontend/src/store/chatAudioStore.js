import { create } from 'zustand';

const useChatAudioStore = create((set) => ({
  activeMessageId: null,
  setActiveMessageId: (id) => set({ activeMessageId: id }),
}));

export default useChatAudioStore;
