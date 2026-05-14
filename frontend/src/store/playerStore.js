import { create } from 'zustand';
import axiosClient from '../api/axiosClient';
import socketClient from '../api/socketClient';

const usePlayerStore = create((set, get) => ({
  currentTrack: null,
  initialSeekTime: null,
  isPlaying: false,
  volume: 0.5,
  previousVolume: 0.5,
  audioUrl: null,
  queue: [],
  loopMode: 'none', // 'none', 'one', 'all'
  broadcastStatus: 'IDLE', // 'IDLE', 'MASTER', 'SYNC'
  activeBroadcastId: null,
  activeBroadcastChatId: null,

  initiateBroadcast: (broadcastId, chatId) => set({ broadcastStatus: 'MASTER', activeBroadcastId: broadcastId, activeBroadcastChatId: chatId }),
  joinBroadcast: (broadcastId) => set({ broadcastStatus: 'SYNC', activeBroadcastId: broadcastId, activeBroadcastChatId: null }),
  leaveBroadcast: () => {
    const { broadcastStatus, activeBroadcastId, activeBroadcastChatId } = get();
    if (broadcastStatus === 'MASTER' && activeBroadcastId && activeBroadcastChatId) {
      socketClient.sendMessage('/chat', {
        chatId: activeBroadcastChatId,
        content: `[BROADCAST_NODE_TERMINATED]:${activeBroadcastId}`,
        sharedTrackId: null,
        sharedTrackTimestamp: null,
        fileUrl: null
      });
    }
    set({ broadcastStatus: 'IDLE', activeBroadcastId: null, activeBroadcastChatId: null });
  },

  playTrack: async (track, newQueue = [], seekTime = null) => {
    // If a new queue is provided, update it
    if (newQueue.length > 0) {
      set({ queue: newQueue });
    }

    // If it's the same track and it's already playing, do nothing
    // If it's the same track and it's paused, toggle play
    if (get().currentTrack?.id === track.id) {
      if (seekTime !== null) {
        set({ initialSeekTime: seekTime });
      }
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
        isPlaying: true,
        initialSeekTime: seekTime
      });
    } catch (error) {
      console.error('Failed to get track URL:', error);
    }
  },
  playNext: (force = false) => {
    const { currentTrack, queue, loopMode } = get();
    if (!currentTrack || queue.length === 0) return;

    if (!force && loopMode === 'one') {
      get().playTrack(currentTrack);
      return;
    }

    const currentIndex = queue.findIndex(t => t.id === currentTrack.id);
    if (currentIndex !== -1 && currentIndex < queue.length - 1) {
      get().playTrack(queue[currentIndex + 1]);
    } else if (loopMode === 'all') {
      get().playTrack(queue[0]);
    } else {
      set({ isPlaying: false }); // Stop at the end of the queue
    }
  },

  playPrevious: () => {
    const { currentTrack, queue, loopMode } = get();
    if (!currentTrack || queue.length === 0) return;

    const currentIndex = queue.findIndex(t => t.id === currentTrack.id);
    if (currentIndex > 0) {
      get().playTrack(queue[currentIndex - 1]);
    } else if (loopMode === 'all') {
      get().playTrack(queue[queue.length - 1]);
    }
  },

  toggleLoopMode: () => {
    const modes = ['none', 'all', 'one'];
    const currentMode = get().loopMode;
    const nextMode = modes[(modes.indexOf(currentMode) + 1) % modes.length];
    set({ loopMode: nextMode });
  },

  togglePlayPause: () => {
    if (!get().currentTrack) return;
    set((state) => ({ isPlaying: !state.isPlaying }));
  },

  pauseForGlobalAudio: () => {
    set({ isPlaying: false });
  },

  setVolume: (volume) => set({ volume }),
  
  toggleMute: () => {
    const { volume, previousVolume } = get();
    if (volume > 0) {
      set({ volume: 0, previousVolume: volume });
    } else {
      set({ volume: previousVolume > 0 ? previousVolume : 0.5 });
    }
  },

  setIsPlaying: (isPlaying) => set({ isPlaying }),
}));

export default usePlayerStore;
