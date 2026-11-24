import { create } from 'zustand';
import toast from 'react-hot-toast';
import axiosClient from '../api/axiosClient';
import socketClient from '../api/socketClient';
import { hostPlayback } from '../sync/broadcastState';

// The player's <audio> element, registered by AudioPlayer, so a broadcast can start with where the host is
let audioElement = null;
export const registerAudioElement = (element) => {
  audioElement = element;
};

export const broadcastSyncDestination = (chatId, broadcastId) => `/broadcast/${chatId}/${broadcastId}/sync`;

// Numbers every state the host sends, increasing also across page loads, so the server can ignore one that
// overtook a newer one: it handles a session's messages in parallel (KAN-109)
let lastBroadcastSequence = 0;
export const nextBroadcastSequence = () => {
  lastBroadcastSequence = Math.max(lastBroadcastSequence + 1, Date.now());
  return lastBroadcastSequence;
};

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
  // A listener who paused locally (or whose player a voice message paused) stays paused until they press play,
  // which rejoins at the host's position (KAN-109)
  broadcastHeld: false,

  /**
   * Starts hosting a broadcast. The server learns about it (START) before the chat message that announces its id
   * is sent, so nobody else can claim it (KAN-110). A host runs one broadcast at a time.
   */
  initiateBroadcast: (broadcastId, chatId) => {
    get().leaveBroadcast();
    set({ broadcastStatus: 'MASTER', activeBroadcastId: broadcastId, activeBroadcastChatId: chatId,
      broadcastHeld: false });
    socketClient.sendMessage(broadcastSyncDestination(chatId, broadcastId), {
      command: 'START',
      ...hostPlayback(get().currentTrack?.id, audioElement),
      sequence: nextBroadcastSequence(),
    });
  },
  /**
   * Starts listening. The listener's own queue and loop mode are dropped, so the end of a track waits for the
   * host's next one instead of playing something else.
   */
  joinBroadcast: (broadcastId, chatId) => {
    get().leaveBroadcast();
    set({ broadcastStatus: 'SYNC', activeBroadcastId: broadcastId, activeBroadcastChatId: chatId,
      broadcastHeld: false, queue: [], loopMode: 'none' });
  },
  leaveBroadcast: () => {
    const { broadcastStatus, activeBroadcastId, activeBroadcastChatId } = get();
    if (broadcastStatus === 'MASTER' && activeBroadcastId && activeBroadcastChatId) {
      // Ends it for the listeners at once; the chat message tells the chat
      socketClient.sendMessage(broadcastSyncDestination(activeBroadcastChatId, activeBroadcastId),
        { command: 'TERMINATE' });
      socketClient.sendMessage('/chat', {
        chatId: activeBroadcastChatId,
        content: `[BROADCAST_NODE_TERMINATED]:${activeBroadcastId}`,
        sharedTrackId: null,
        sharedTrackTimestamp: null,
        fileUrl: null
      });
    }
    set({ broadcastStatus: 'IDLE', activeBroadcastId: null, activeBroadcastChatId: null, broadcastHeld: false });
  },
  setBroadcastHeld: (held) => set({ broadcastHeld: held }),

  /**
   * Plays a track. `autoplay: false` loads it paused (a listener follows the host's play state). A listener who
   * picks a track themselves leaves the broadcast; the broadcast's own loads pass `fromBroadcast`.
   */
  playTrack: async (track, newQueue = [], seekTime = null, { autoplay = true, fromBroadcast = false } = {}) => {
    if (get().broadcastStatus === 'SYNC' && !fromBroadcast) {
      get().leaveBroadcast();
      toast('Left the broadcast to play your own track');
    }
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
      set({ isPlaying: autoplay });
      return;
    }

    try {
      // Fetch presigned URL from backend
      const response = await axiosClient.get(`/api/tracks/${track.id}/url`);
      const url = response.data;

      set({
        currentTrack: track,
        audioUrl: url,
        isPlaying: autoplay,
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
    if (get().broadcastStatus === 'SYNC') {
      // A listener pauses locally, and play rejoins at the host's position (AudioPlayer follows broadcastHeld)
      set((state) => ({ broadcastHeld: state.isPlaying, isPlaying: false }));
      return;
    }
    set((state) => ({ isPlaying: !state.isPlaying }));
  },

  pauseForGlobalAudio: () => {
    // A listener's broadcast stays paused after a voice message, until they press play
    set((state) => ({ isPlaying: false, broadcastHeld: state.broadcastStatus === 'SYNC' }));
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
