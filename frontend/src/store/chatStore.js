import { create } from 'zustand';
import axiosClient from '../api/axiosClient';
import socketClient from '../api/socketClient';

const useChatStore = create((set, get) => ({
  chats: [],
  messages: [], // Current active chat messages
  activeChat: null,
  loading: false,
  messagesLoading: false,

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

  sendMessage: (content) => {
    const { activeChat } = get();
    if (!activeChat) return;

    const messageDto = {
      chatId: activeChat.id,
      content: content,
      // recipientId can be used for new chats, but here we have activeChat.id
    };

    socketClient.sendMessage('/chat', messageDto);
  }
}));

export default useChatStore;
