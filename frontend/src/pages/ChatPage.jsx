import React, { useEffect, useState, useRef } from 'react';
import { Send, MessageCircle, Search, Music, Loader2, Plus, X, Play, Paperclip } from 'lucide-react';
import useChatStore from '../store/chatStore';
import useAuthStore from '../store/authStore';
import usePlayerStore from '../store/playerStore';
import NewChatModal from '../components/chat/NewChatModal';
import ChatAudioPlayer from '../components/chat/ChatAudioPlayer';
import axiosClient from '../api/axiosClient';

const ChatPage = () => {
  const { user: currentUser } = useAuthStore();
  const { 
    chats, activeChat, messages, loading, messagesLoading, activeAttachment,
    fetchChats, setActiveChat, sendMessage, initSocket, closeSocket, startChatWithUser, clearAttachment
  } = useChatStore();
  const { playTrack } = usePlayerStore();
  const [messageText, setMessageText] = useState('');
  const [isNewChatModalOpen, setIsNewChatModalOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const messagesEndRef = useRef(null);
  const fileInputRef = useRef(null);

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
    if (!messageText.trim() && !activeAttachment) return;
    sendMessage(messageText);
    setMessageText('');
  };

  const formatTime = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;

    setUploading(true);
    const formData = new FormData();
    formData.append('file', file);

    try {
      const res = await axiosClient.post('/api/messages/upload', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });
      sendMessage("", res.data);
    } catch (error) {
      console.error("Failed to upload audio", error);
    } finally {
      setUploading(false);
      e.target.value = null; // reset input
    }
  };

  const chatsList = chats.filter(c => c.status !== 'REJECTED' && (c.status === 'ACCEPTED' || c.initiatorId === currentUser?.userId || !c.initiatorId));
  const requestsList = chats.filter(c => c.status === 'PENDING' && c.initiatorId && c.initiatorId !== currentUser?.userId);

  const renderChat = (chat) => {
    const otherUser = getOtherParticipant(chat);
    const isActive = activeChat?.id === chat.id;
    return (
      <button
        key={chat.id}
        onClick={() => setActiveChat(chat)}
        className={`w-full p-6 flex items-center gap-4 transition-all border-b border-border/50 ${
          isActive ? 'bg-white/5' : 'hover:bg-white/[0.02]'
        }`}
      >
        <div className="relative flex-shrink-0">
          <div className="w-10 h-10 bg-zinc-800 flex items-center justify-center text-[10px] font-black text-white">
            {otherUser?.displayName?.charAt(0)}
          </div>
        </div>
        <div className="flex-1 text-left overflow-hidden">
          <div className="flex justify-between items-baseline mb-1">
            <p className="text-[11px] font-black uppercase tracking-tight text-white truncate">{otherUser?.displayName}</p>
            {chat.lastMessage && (
              <span className="text-[9px] font-medium text-muted tabular-nums">
                {formatTime(chat.lastMessage.timestamp)}
              </span>
            )}
          </div>
          <p className={`text-[10px] font-medium uppercase tracking-wider truncate ${isActive ? 'text-zinc-300' : 'text-muted'}`}>
            {chat.lastMessage 
              ? (chat.lastMessage.content || (chat.lastMessage.fileUrl ? 'Recording' : (chat.lastMessage.sharedTrack ? 'Shared Track' : 'Connection Initialized')))
              : 'New Connection'}
          </p>
        </div>
      </button>
    );
  };

  return (
    <div className="flex h-[calc(100vh-160px)] bg-surface border border-border overflow-hidden">
      {/* Chats Sidebar */}
      <div className="w-80 border-r border-border flex flex-col bg-background/50">
        <div className="p-8 border-b border-border">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-sm font-black uppercase tracking-[0.3em]">Archive</h2>
            <button 
              onClick={() => setIsNewChatModalOpen(true)}
              className="p-2 border border-border hover:border-white transition-colors text-muted hover:text-white"
            >
              <Plus size={16} />
            </button>
          </div>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" size={14} />
            <input 
              type="text" 
              placeholder="SEARCH IDENTITIES..." 
              className="w-full bg-background border border-border py-2.5 pl-10 pr-4 text-[10px] font-bold uppercase tracking-widest focus:outline-none focus:border-white transition-all"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex flex-col items-center justify-center p-12">
              <Loader2 className="animate-spin text-muted mb-2" />
              <p className="text-[10px] font-black uppercase tracking-widest text-muted">Loading...</p>
            </div>
          ) : chats.length === 0 ? (
            <div className="p-12 text-center">
              <p className="text-[10px] font-black uppercase tracking-widest text-muted">No transmissions found</p>
            </div>
          ) : (
            <>
              {requestsList.length > 0 && (
                <div className="mb-4">
                  <div className="px-6 py-2 bg-white/5 border-y border-border/50 text-[9px] font-black uppercase tracking-widest text-muted">
                    Pending Requests ({requestsList.length})
                  </div>
                  {requestsList.map(chat => renderChat(chat))}
                </div>
              )}
              {chatsList.length > 0 && (
                <div>
                  <div className="px-6 py-2 bg-white/5 border-y border-border/50 text-[9px] font-black uppercase tracking-widest text-muted">
                    Active Connections
                  </div>
                  {chatsList.map(chat => renderChat(chat))}
                </div>
              )}
            </>
          )}
        </div>
      </div>

      {/* Conversation Area */}
      <div className="flex-1 flex flex-col bg-background relative">
        {activeChat ? (
          <>
            {/* Chat Header */}
            <div className="p-6 px-8 border-b border-border flex items-center justify-between bg-surface/50 backdrop-blur-md z-10">
              <div className="flex items-center gap-4">
                <div className="w-8 h-8 bg-zinc-800 flex items-center justify-center text-[10px] font-black text-white">
                  {getOtherParticipant(activeChat)?.displayName?.charAt(0)}
                </div>
                <div>
                  <h3 className="text-xs font-black uppercase tracking-widest text-white leading-tight">
                    {getOtherParticipant(activeChat)?.displayName}
                  </h3>
                  <div className="flex items-center gap-2 mt-1">
                    <div className="w-1.5 h-1.5 bg-white rounded-full"></div>
                    <span className="text-[9px] text-muted font-black uppercase tracking-widest">Active State</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Messages */}
            <div className="flex-1 p-8 overflow-y-auto space-y-6 scroll-smooth">
              {messagesLoading ? (
                <div className="h-full flex items-center justify-center">
                  <Loader2 className="animate-spin text-muted h-6 w-6" />
                </div>
              ) : messages.length === 0 ? (
                <div className="flex flex-col items-center justify-center h-full text-muted space-y-4">
                  <p className="text-[10px] font-black uppercase tracking-[0.3em]">Connection Initialized</p>
                </div>
              ) : (
                messages.map((msg, idx) => {
                  const isOwn = msg.sender.id === currentUser?.userId;
                  return (
                    <div key={msg.id || idx} className={`flex ${isOwn ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[70%]`}>
                        {msg.sharedTrack && (
                          <div className={`mb-2 p-2 flex items-center gap-3 transition-colors cursor-pointer border ${isOwn ? 'bg-white/10 border-white/20 hover:bg-white/20' : 'bg-surface border-border hover:bg-zinc-800'}`} onClick={() => playTrack(msg.sharedTrack)}>
                            <div className="relative w-10 h-10 flex-shrink-0">
                              {msg.sharedTrack.coverUrl ? (
                                <img src={msg.sharedTrack.coverUrl} alt="Cover" className="w-full h-full object-cover grayscale" />
                              ) : (
                                <div className="w-full h-full bg-zinc-800 flex items-center justify-center"><Music size={12} className="text-muted" /></div>
                              )}
                              <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 hover:opacity-100 transition-opacity">
                                <Play size={14} className="text-white ml-0.5" />
                              </div>
                            </div>
                            <div className="overflow-hidden">
                              <p className={`text-[10px] font-black uppercase tracking-tight truncate ${isOwn ? 'text-white' : 'text-white'}`}>{msg.sharedTrack.title}</p>
                              <p className={`text-[9px] font-medium uppercase tracking-widest truncate ${isOwn ? 'text-zinc-400' : 'text-muted'}`}>{msg.sharedTrack.authorName}</p>
                            </div>
                          </div>
                        )}
                        {msg.content && (
                          <div className={`px-5 py-3 text-sm transition-all ${
                            isOwn 
                              ? 'bg-white text-black font-medium' 
                              : 'bg-surface text-white border border-border'
                          }`}>
                            {msg.content}
                          </div>
                        )}
                        {msg.fileUrl && (
                          <div className="mt-2">
                            <ChatAudioPlayer url={msg.fileUrl} id={msg.id} />
                          </div>
                        )}
                        <p className={`text-[9px] mt-2 font-medium text-muted tabular-nums uppercase tracking-widest ${isOwn ? 'text-right' : 'text-left'}`}>
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
            {activeChat.status === 'PENDING' && activeChat.initiatorId !== currentUser?.userId ? (
              <div className="p-8 bg-transparent border-t border-border">
                <div className="border border-border p-6 bg-surface text-center">
                  <p className="text-[10px] font-bold uppercase tracking-widest text-white mb-6">
                    {getOtherParticipant(activeChat)?.displayName} wants to connect and send you transmissions.
                  </p>
                  <div className="flex justify-center gap-4">
                    <button 
                      onClick={() => useChatStore.getState().acceptChat(activeChat.id)}
                      className="px-8 py-3 bg-white text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-all"
                    >
                      Accept
                    </button>
                    <button 
                      onClick={() => useChatStore.getState().rejectChat(activeChat.id)}
                      className="px-8 py-3 border border-border text-white text-[10px] font-black uppercase tracking-widest hover:border-white transition-all"
                    >
                      Decline
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              <div className="bg-transparent border-t border-border relative">
                {/* Active Attachment Preview */}
                {activeAttachment && (
                  <div className="absolute bottom-full left-0 right-0 p-3 bg-white/10 backdrop-blur-md border-t border-white/10 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      {activeAttachment.coverUrl ? (
                        <img src={activeAttachment.coverUrl} className="w-8 h-8 object-cover grayscale" alt="attachment" />
                      ) : (
                        <div className="w-8 h-8 bg-zinc-800 flex items-center justify-center"><Music size={12} className="text-muted" /></div>
                      )}
                      <div>
                        <p className="text-[10px] font-black uppercase tracking-tight text-white">{activeAttachment.title}</p>
                        <p className="text-[9px] font-medium uppercase tracking-widest text-zinc-400">{activeAttachment.authorName}</p>
                      </div>
                    </div>
                    <button onClick={clearAttachment} className="text-zinc-400 hover:text-white p-1">
                      <X size={14} />
                    </button>
                  </div>
                )}
                <form 
                  onSubmit={handleSend}
                  className="flex items-center gap-4 border border-border p-1 bg-surface m-8 mt-6"
                >
                  <input 
                    type="file"
                    accept="audio/*"
                    className="hidden"
                    ref={fileInputRef}
                    onChange={handleFileUpload}
                  />
                  <button 
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploading}
                    className="p-3 text-muted hover:text-white transition-colors disabled:opacity-50"
                  >
                    {uploading ? <Loader2 size={16} className="animate-spin" /> : <Paperclip size={16} />}
                  </button>
                  <input 
                    value={messageText}
                    onChange={(e) => setMessageText(e.target.value)}
                    placeholder="INPUT TRANSMISSION..." 
                    className="flex-1 bg-transparent px-2 py-3 text-[10px] font-bold uppercase tracking-widest focus:outline-none text-white placeholder-zinc-700"
                  />
                  <button 
                    type="submit"
                    disabled={!messageText.trim() && !activeAttachment}
                    className="px-6 py-3 bg-white text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-all disabled:opacity-30"
                  >
                    Send
                  </button>
                </form>
              </div>
            )}
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-muted text-center p-12">
            <h3 className="text-xl font-black text-white mb-4 uppercase tracking-tighter">Secure Messaging</h3>
            <p className="max-w-xs text-[10px] font-bold uppercase tracking-widest leading-loose">
              Establish a connection with artists in the network. Server-side encrypted digital transmission active.
            </p>
          </div>
        )}
      </div>

      {isNewChatModalOpen && (
        <NewChatModal 
          onClose={() => setIsNewChatModalOpen(false)}
          onSelectUser={async (userId) => {
            setIsNewChatModalOpen(false);
            await startChatWithUser(userId);
          }}
        />
      )}
    </div>
  );
};

export default ChatPage;
