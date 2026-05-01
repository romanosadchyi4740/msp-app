import { create } from 'zustand';
import axiosClient from '../api/axiosClient';
import socketClient from '../api/socketClient';

const useChatStore = create((set, get) => ({
  chats: [],
  messages: [], // Current active chat messages
  activeChat: null,
  activeAttachment: null,
  loading: false,
  messagesLoading: false,

  shareModal: {
    isOpen: false,
    track: null,
    timestamp: null
  },

  openShareModal: (track, timestamp) => set({ shareModal: { isOpen: true, track, timestamp } }),
  closeShareModal: () => set({ shareModal: { isOpen: false, track: null, timestamp: null } }),

  setAttachment: (track) => set({ activeAttachment: track }),
  clearAttachment: () => set({ activeAttachment: null }),

  fetchChats: async () => {
    set({ loading: true });
    try {
      const response = await axiosClient.get('/api/chats');
      set({ chats: response.data, loading: false });
    } catch (error) {
      console.error('Failed to fetch chats:', error);
      set({ loading: false });
    }
  },

  fetchMessages: async (chatId) => {
    set({ messagesLoading: true });
    try {
      const response = await axiosClient.get(`/api/chats/${chatId}/messages`, {
        params: { size: 50, sort: 'timestamp,asc' }
      });
      // Backend returns a Page object, let's check its structure
      // Usually messages are in .content
      const messageList = response.data.content || response.data;
      // We want them sorted by timestamp ASC for the UI
      const sortedMessages = [...messageList].sort((a, b) => 
        new Date(a.timestamp) - new Date(b.timestamp)
      );
      set({ messages: sortedMessages, messagesLoading: false });
    } catch (error) {
      console.error('Failed to fetch messages:', error);
      set({ messagesLoading: false });
    }
  },

  acceptChat: async (chatId) => {
    try {
      const response = await axiosClient.put(`/api/chats/${chatId}/accept`);
      const updatedChat = response.data;
      set(state => ({
        chats: state.chats.map(c => c.id === chatId ? updatedChat : c),
        activeChat: state.activeChat?.id === chatId ? updatedChat : state.activeChat
      }));
    } catch (error) {
      console.error('Failed to accept chat:', error);
    }
  },

  rejectChat: async (chatId) => {
    try {
      await axiosClient.put(`/api/chats/${chatId}/reject`);
      set(state => ({
        chats: state.chats.filter(c => c.id !== chatId),
        activeChat: state.activeChat?.id === chatId ? null : state.activeChat
      }));
    } catch (error) {
      console.error('Failed to reject chat:', error);
    }
  },

  startChatWithUser: async (recipientId) => {
    try {
      const response = await axiosClient.post(`/api/chats/with/${recipientId}`);
      const chat = response.data;
      
      const existingChats = get().chats;
      if (!existingChats.find(c => c.id === chat.id)) {
        set({ chats: [chat, ...existingChats] });
      }
      
      get().setActiveChat(chat);
      return chat;
    } catch (error) {
      console.error('Failed to start chat:', error);
      throw error;
    }
  },

  createCluster: async (groupName, participantIds) => {
    try {
      const response = await axiosClient.post(`/api/chats/cluster`, { groupName, participantIds });
      const chat = response.data;
      
      const existingChats = get().chats;
      set({ chats: [chat, ...existingChats] });
      
      get().setActiveChat(chat);
      return chat;
    } catch (error) {
      console.error('Failed to create cluster:', error);
      throw error;
    }
  },

  setActiveChat: (chat) => {
    set({ activeChat: chat, messages: [] });
    if (chat) {
      get().fetchMessages(chat.id);
    }
  },

  // WebSocket actions
  initSocket: (token) => {
    socketClient.connect(token, (newMessage) => {
      // Handle incoming message
      const { activeChat, chats } = get();
      
      // If message belongs to active chat, add it
      if (activeChat && newMessage.chatId === activeChat.id) {
        set((state) => ({
          messages: [...state.messages, newMessage]
        }));
      }

      // Update chats list last message
      const updatedChats = chats.map(c => {
        if (c.id === newMessage.chatId) {
          return { ...c, lastMessage: newMessage };
        }
        return c;
      });
      set({ chats: updatedChats });
    });
  },

  closeSocket: () => {
    socketClient.disconnect();
  },

  sendMessage: (content, fileUrl = null) => {
    const { activeChat, activeAttachment } = get();
    if (!activeChat) return;

    const messageDto = {
      chatId: activeChat.id,
      content: content,
      sharedTrackId: activeAttachment?.id || null,
      sharedTrackTimestamp: activeAttachment?.shareTimestamp || null,
      fileUrl: fileUrl
    };

    socketClient.sendMessage('/chat', messageDto);
    get().clearAttachment();
  }
}));

export default useChatStore;
