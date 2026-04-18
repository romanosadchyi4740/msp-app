import React, { useEffect, useState, useRef } from 'react';
import { Send, MessageCircle, Search, Music, Loader2 } from 'lucide-react';
import useChatStore from '../store/chatStore';
import useAuthStore from '../store/authStore';

const ChatPage = () => {
  const { user: currentUser } = useAuthStore();
  const { 
    chats, activeChat, messages, loading, messagesLoading,
    fetchChats, setActiveChat, sendMessage, initSocket, closeSocket 
  } = useChatStore();
  const [messageText, setMessageText] = useState('');
  const messagesEndRef = useRef(null);

  // Initialize socket on mount
  useEffect(() => {
    const token = localStorage.getItem('token');
    if (token) {
      initSocket(token);
    }
    fetchChats();
    return () => closeSocket();
  }, [initSocket, closeSocket, fetchChats]);

  // Scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const getOtherParticipant = (chat) => {
    return chat.participants.find(p => p.id !== currentUser?.userId);
  };

  const handleSend = (e) => {
    e.preventDefault();
    if (!messageText.trim()) return;
    sendMessage(messageText);
    setMessageText('');
  };

  const formatTime = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="flex h-[calc(100vh-160px)] bg-white/5 rounded-3xl overflow-hidden border border-white/10 shadow-2xl">
      {/* Chats Sidebar */}
      <div className="w-80 border-r border-white/10 flex flex-col bg-black/40 backdrop-blur-xl">
        <div className="p-6 border-b border-white/10">
          <h2 className="text-2xl font-bold mb-4 tracking-tight">Messages</h2>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-500" size={18} />
            <input 
              type="text" 
              placeholder="Search conversations..." 
              className="w-full bg-white/5 border border-white/10 rounded-2xl py-2.5 pl-10 pr-4 text-sm focus:outline-none focus:border-primary/50 transition-all focus:ring-1 focus:ring-primary/20"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex flex-col items-center justify-center p-12">
              <Loader2 className="animate-spin text-primary/40 mb-2" />
              <p className="text-xs text-gray-500">Loading chats...</p>
            </div>
          ) : chats.length === 0 ? (
            <div className="p-12 text-center">
              <div className="w-16 h-16 bg-white/5 rounded-full flex items-center justify-center mx-auto mb-4">
                <MessageCircle className="text-gray-600" size={32} />
              </div>
              <p className="text-sm text-gray-400">No conversations yet</p>
            </div>
          ) : (
            chats.map((chat) => {
              const otherUser = getOtherParticipant(chat);
              const isActive = activeChat?.id === chat.id;
              return (
                <button
                  key={chat.id}
                  onClick={() => setActiveChat(chat)}
                  className={`w-full p-4 flex items-center gap-4 transition-all hover:bg-white/5 border-l-4 ${
                    isActive ? 'bg-primary/10 border-primary' : 'border-transparent'
                  }`}
                >
                  <div className="relative flex-shrink-0">
                    <div className="w-12 h-12 rounded-full bg-gradient-to-br from-primary/30 to-primary/10 flex items-center justify-center text-primary font-bold shadow-inner">
                      {otherUser?.displayName?.charAt(0)}
                    </div>
                    <div className="absolute bottom-0 right-0 w-3.5 h-3.5 bg-green-500 border-2 border-dark rounded-full"></div>
                  </div>
                  <div className="flex-1 text-left overflow-hidden">
                    <div className="flex justify-between items-baseline mb-0.5">
                      <p className="font-bold truncate text-sm text-white">{otherUser?.displayName}</p>
                      {chat.lastMessage && (
                        <span className="text-[10px] text-gray-500">
                          {formatTime(chat.lastMessage.timestamp)}
                        </span>
                      )}
                    </div>
                    <p className={`text-xs truncate ${isActive ? 'text-gray-300' : 'text-gray-500'}`}>
                      {chat.lastMessage?.content || 'Started a conversation'}
                    </p>
                  </div>
                </button>
              );
            })
          )}
        </div>
      </div>

      {/* Conversation Area */}
      <div className="flex-1 flex flex-col bg-black/20 relative">
        {activeChat ? (
          <>
            {/* Chat Header */}
            <div className="p-4 px-6 border-b border-white/10 flex items-center justify-between bg-white/5 backdrop-blur-md z-10">
              <div className="flex items-center gap-4">
                <div className="w-10 h-10 rounded-full bg-primary/20 flex items-center justify-center text-primary font-bold border border-primary/20">
                  {getOtherParticipant(activeChat)?.displayName?.charAt(0)}
                </div>
                <div>
                  <h3 className="font-bold text-white leading-tight">
                    {getOtherParticipant(activeChat)?.displayName}
                  </h3>
                  <div className="flex items-center gap-1.5">
                    <div className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></div>
                    <span className="text-[10px] text-green-500 font-medium uppercase tracking-wider">Active Now</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Messages */}
            <div className="flex-1 p-6 overflow-y-auto space-y-4 scroll-smooth">
              {messagesLoading ? (
                <div className="h-full flex items-center justify-center">
                  <Loader2 className="animate-spin text-primary h-8 w-8" />
                </div>
              ) : messages.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full text-gray-500 space-y-4">
                  <div className="p-6 bg-white/5 rounded-3xl border border-white/5 max-w-sm text-center">
                    <Music className="mx-auto mb-4 opacity-20" size={48} />
                    <p className="text-sm font-medium text-gray-300">Share some music!</p>
                    <p className="text-xs mt-2">This is the beginning of your chat with {getOtherParticipant(activeChat)?.displayName}.</p>
                  </div>
                </div>
              ) : (
                messages.map((msg, idx) => {
                  const isOwn = msg.sender.id === currentUser?.userId;
                  return (
                    <div key={msg.id || idx} className={`flex ${isOwn ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[70%] group`}>
                        <div className={`px-4 py-2.5 rounded-2xl text-sm shadow-lg ${
                          isOwn 
                            ? 'bg-primary text-black font-medium rounded-tr-none' 
                            : 'bg-white/10 text-white rounded-tl-none border border-white/5'
                        }`}>
                          {msg.content}
                        </div>
                        <p className={`text-[10px] mt-1 text-gray-500 ${isOwn ? 'text-right' : 'text-left'}`}>
                          {formatTime(msg.timestamp || new Date())}
                        </p>
                      </div>
                    </div>
                  );
                })
              )}
              <div ref={messagesEndRef} />
            </div>

            {/* Input Area */}
            <div className="p-6 bg-transparent border-t border-white/10">
              <form 
                onSubmit={handleSend}
                className="flex items-center gap-3 bg-white/5 p-1.5 rounded-2xl border border-white/10 focus-within:border-primary/40 focus-within:ring-1 focus-within:ring-primary/20 transition-all"
              >
                <input 
                  value={messageText}
                  onChange={(e) => setMessageText(e.target.value)}
                  placeholder="Type a message..." 
                  className="flex-1 bg-transparent px-4 py-2 text-sm focus:outline-none text-white"
                />
                <button 
                  type="submit"
                  disabled={!messageText.trim()}
                  className="w-10 h-10 rounded-xl bg-primary flex items-center justify-center text-black hover:scale-105 active:scale-95 transition-all disabled:opacity-50 disabled:hover:scale-100 shadow-lg shadow-primary/20"
                >
                  <Send size={18} fill="currentColor" />
                </button>
              </form>
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-gray-500 text-center p-8">
            <div className="w-24 h-24 bg-white/5 rounded-full flex items-center justify-center mb-6 border border-white/5">
              <MessageCircle size={48} className="text-primary/20" />
            </div>
            <h3 className="text-2xl font-bold text-white mb-2">Direct Messages</h3>
            <p className="max-w-xs text-sm leading-relaxed">
              Connect with artists, share tracks, and grow your musical network. Select a chat to begin.
            </p>
          </div>
        )}
      </div>
    </div>
  );
};

export default ChatPage;
