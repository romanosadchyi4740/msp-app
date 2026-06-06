import React, {useEffect, useRef, useState} from 'react';
import {Loader2, Music, Network, Paperclip, Play, Plus, Radio, Search, X} from 'lucide-react';
import useChatStore from '../store/chatStore';
import useAuthStore from '../store/authStore';
import usePlayerStore from '../store/playerStore';
import NewChatModal from '../components/chat/NewChatModal';
import ChatAudioPlayer from '../components/chat/ChatAudioPlayer';
import ClusterDetailsModal from '../components/modals/ClusterDetailsModal';
import axiosClient from '../api/axiosClient';

const ChatPage = () => {
  const { user: currentUser } = useAuthStore();
  const { 
    chats, activeChat, messages, loading, messagesLoading, activeAttachment,
    fetchChats, setActiveChat, sendMessage, startChatWithUser, createCluster, clearAttachment
  } = useChatStore();
  const { playTrack, initiateBroadcast, joinBroadcast, broadcastStatus, activeBroadcastId } = usePlayerStore();
  const [messageText, setMessageText] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [isNewChatModalOpen, setIsNewChatModalOpen] = useState(false);
  const [isClusterDetailsOpen, setIsClusterDetailsOpen] = useState(false);
  const [uploading, setUploading] = useState(false);
  const messagesEndRef = useRef(null);
  const fileInputRef = useRef(null);

  // Initialize socket on mount
  useEffect(() => {
    fetchChats();
  }, [fetchChats]);

  // Scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const getOtherParticipant = (chat) => {
    return chat.participants?.find(p => p.id !== currentUser?.userId);
  };

  const handleSend = (e) => {
    e.preventDefault();
    if (!messageText.trim() && !activeAttachment) return;
    sendMessage(messageText);
    setMessageText('');
  };

  const handleInitBroadcast = () => {
    const broadcastId = crypto.randomUUID();
    initiateBroadcast(broadcastId, activeChat.id);
    sendMessage(`[BROADCAST_NODE_INIT]:${broadcastId}`);
  };

  const terminatedBroadcastIds = React.useMemo(() => {
    const terminated = new Set();
    messages.forEach(msg => {
      if (msg.content && msg.content.startsWith('[BROADCAST_NODE_TERMINATED]:')) {
        terminated.add(msg.content.split(':')[1]);
      }
    });
    return terminated;
  }, [messages]);

  const formatTime = (timestamp) => {
    return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  const formatAudioTime = (time) => {
    if (isNaN(time)) return "0:00";
    const minutes = Math.floor(time / 60);
    const seconds = Math.floor(time % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
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

  const filterByQuery = (chat) => {
    if (!searchQuery.trim()) return true;
    const query = searchQuery.toLowerCase();
    if (chat.isGroup) {
      return chat.name?.toLowerCase().includes(query);
    } else {
      const otherUser = getOtherParticipant(chat);
      return otherUser?.displayName?.toLowerCase().includes(query);
    }
  };

  const chatsList = chats
    .filter(c => c.status !== 'REJECTED' && (c.status === 'ACCEPTED' || c.initiatorId === currentUser?.userId || !c.initiatorId))
    .filter(filterByQuery);
    
  const requestsList = chats
    .filter(c => c.status === 'PENDING' && c.initiatorId && c.initiatorId !== currentUser?.userId)
    .filter(filterByQuery);

  const renderChat = (chat) => {
    const isGroup = chat.isGroup;
    const otherUser = !isGroup ? getOtherParticipant(chat) : null;
    const isActive = activeChat?.id === chat.id;
    return (
      <button
        key={chat.id}
        onClick={() => setActiveChat(chat)}
        className={`w-full p-6 flex items-center gap-4 transition-all border-b border-zinc-200 dark:border-zinc-800/50 ${
          isActive ? 'bg-zinc-100 dark:bg-white/5' : 'hover:bg-zinc-50 dark:hover:bg-white/[0.02]'
        }`}
      >
        <div className="relative flex-shrink-0">
          <div className="w-10 h-10 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] font-black text-zinc-900 dark:text-white">
            {isGroup ? <Network size={16} /> : otherUser?.displayName?.charAt(0)}
          </div>
        </div>
        <div className="flex-1 text-left overflow-hidden">
          <div className="flex justify-between items-baseline mb-1">
            <p className="text-[11px] font-black uppercase tracking-tight text-zinc-900 dark:text-white truncate">
              {isGroup ? chat.name : otherUser?.displayName}
            </p>
            {chat.lastMessage && (
              <span className="text-[9px] font-medium text-zinc-500 dark:text-zinc-400 tabular-nums">
                {formatTime(chat.lastMessage.timestamp)}
              </span>
            )}
          </div>
          <p className={`text-[10px] font-medium uppercase tracking-wider truncate ${isActive ? 'text-zinc-700 dark:text-zinc-300' : 'text-zinc-500 dark:text-zinc-500'}`}>
            {chat.lastMessage 
              ? (chat.lastMessage.content || (chat.lastMessage.fileUrl ? 'Recording' : (chat.lastMessage.sharedTrack ? 'Shared Track' : 'Connection Initialized')))
              : 'New Connection'}
          </p>
        </div>
      </button>
    );
  };

  return (
    <div className="flex h-[calc(100vh-160px)] bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 overflow-hidden transition-colors duration-300">
      {/* Chats Sidebar */}
      <div className="w-80 border-r border-zinc-200 dark:border-zinc-800 flex flex-col bg-zinc-50/50 dark:bg-zinc-950/50">
        <div className="p-8 border-b border-zinc-200 dark:border-zinc-800">
          <div className="flex items-center justify-between mb-6">
            <h2 className="text-sm font-black uppercase tracking-[0.3em] text-zinc-900 dark:text-white">Comms Link</h2>
            <button 
              onClick={() => setIsNewChatModalOpen(true)}
              className="p-2 border border-zinc-200 dark:border-zinc-800 hover:border-zinc-900 dark:hover:border-white transition-colors text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white"
            >
              <Plus size={16} />
            </button>
          </div>
          <div className="relative">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400 dark:text-zinc-500" size={14} />
            <input 
              type="text" 
              placeholder="SEARCH IDENTITIES..." 
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full bg-zinc-50 dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 py-2.5 pl-10 pr-4 text-[10px] font-bold uppercase tracking-widest focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-all text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto">
          {loading ? (
            <div className="flex flex-col items-center justify-center p-12">
              <Loader2 className="animate-spin text-zinc-400 dark:text-zinc-500 mb-2" />
              <p className="text-[10px] font-black uppercase tracking-widest text-zinc-400 dark:text-zinc-500">Loading...</p>
            </div>
          ) : (chatsList.length === 0 && requestsList.length === 0) ? (
            <div className="p-12 text-center">
              <p className="text-[10px] font-black uppercase tracking-widest text-zinc-400 dark:text-zinc-500">
                {searchQuery ? "No active nodes match your query" : "No transmissions found"}
              </p>
            </div>
          ) : (
            <>
              {requestsList.length > 0 && (
                <div className="mb-4">
                  <div className="px-6 py-2 bg-zinc-100 dark:bg-white/5 border-y border-zinc-200 dark:border-zinc-800/50 text-[9px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">
                    Pending Requests ({requestsList.length})
                  </div>
                  {requestsList.map(chat => renderChat(chat))}
                </div>
              )}
              {chatsList.length > 0 && (
                <div>
                  <div className="px-6 py-2 bg-zinc-100 dark:bg-white/5 border-y border-zinc-200 dark:border-zinc-800/50 text-[9px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">
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
      <div className="flex-1 flex flex-col bg-zinc-50 dark:bg-zinc-950 relative">
        {activeChat ? (
          <>
            {/* Chat Header */}
            <div className="p-6 px-8 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between bg-white/50 dark:bg-zinc-900/50 backdrop-blur-md z-10">
              <div className="flex items-center gap-4">
                <div className="w-8 h-8 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] font-black text-zinc-900 dark:text-white">
                  {activeChat.isGroup ? <Network size={16} /> : getOtherParticipant(activeChat)?.displayName?.charAt(0)}
                </div>
                {activeChat.isGroup ? (
                  <button 
                    onClick={() => setIsClusterDetailsOpen(true)}
                    className="text-left group flex flex-col items-start hover:opacity-80 transition-opacity"
                  >
                    <div className="flex items-center gap-2">
                      <h3 className="text-xs font-black uppercase tracking-widest text-zinc-900 dark:text-white leading-tight">
                        {activeChat.name}
                      </h3>
                      <span className="text-[8px] border border-zinc-900/20 dark:border-white/20 bg-zinc-900/5 dark:bg-white/5 px-1 font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 group-hover:text-zinc-900 dark:group-hover:text-white transition-colors">
                        Info
                      </span>
                    </div>
                    <div className="flex items-center gap-2 mt-1">
                      <div className="w-1.5 h-1.5 bg-zinc-900 dark:bg-white rounded-full"></div>
                      <span className="text-[9px] text-zinc-500 dark:text-zinc-400 font-black uppercase tracking-widest">Active State</span>
                    </div>
                  </button>
                ) : (
                  <div>
                    <h3 className="text-xs font-black uppercase tracking-widest text-zinc-900 dark:text-white leading-tight">
                      {getOtherParticipant(activeChat)?.displayName}
                    </h3>
                    <div className="flex items-center gap-2 mt-1">
                      <div className="w-1.5 h-1.5 bg-zinc-900 dark:bg-white rounded-full"></div>
                      <span className="text-[9px] text-zinc-500 dark:text-zinc-400 font-black uppercase tracking-widest">Active State</span>
                    </div>
                  </div>
                )}
              </div>
              
              {activeChat.isGroup && (
                <button 
                  onClick={handleInitBroadcast}
                  className="flex items-center gap-1.5 px-3 py-1 bg-zinc-900 text-white dark:bg-white dark:text-black text-[9px] font-black uppercase tracking-widest hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-colors"
                >
                  <Radio size={12} />
                  Initialize Broadcast Node
                </button>
              )}
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
                  const isBroadcastTerminatedMsg = msg.content && msg.content.startsWith('[BROADCAST_NODE_TERMINATED]:');
                  
                  if (isBroadcastTerminatedMsg) {
                    return (
                      <div key={msg.id || idx} className="flex justify-center my-4">
                        <span className="px-3 py-1 bg-white/5 border border-white/10 text-[9px] font-black uppercase tracking-widest text-muted">
                          Broadcast Node Terminated
                        </span>
                      </div>
                    );
                  }

                  const isBroadcastInit = msg.content && msg.content.startsWith('[BROADCAST_NODE_INIT]:');
                  const broadcastId = isBroadcastInit ? msg.content.split(':')[1] : null;
                  const isTerminated = isBroadcastInit && terminatedBroadcastIds.has(broadcastId);

                  return (
                    <div key={msg.id || idx} className={`flex ${isOwn ? 'justify-end' : 'justify-start'}`}>
                      <div className={`max-w-[70%]`}>
                        {activeChat.isGroup && !isOwn && (
                          <p className="text-[9px] font-black uppercase tracking-widest text-zinc-500 mb-1 ml-1">{msg.sender.displayName}</p>
                        )}
                        {msg.sharedTrack && (
                          <div className={`mb-2 p-2 flex items-center gap-3 transition-colors cursor-pointer border ${isOwn ? 'bg-zinc-900 border-zinc-800 hover:bg-zinc-800 dark:bg-white/10 dark:border-white/20 dark:hover:bg-white/20' : 'bg-zinc-50 dark:bg-zinc-900 border-zinc-200 dark:border-zinc-800 hover:bg-zinc-100 dark:hover:bg-zinc-800'}`} onClick={() => playTrack(msg.sharedTrack, [], msg.sharedTrackTimestamp != null ? msg.sharedTrackTimestamp : null)}>
                            <div className="relative w-10 h-10 flex-shrink-0">
                              {msg.sharedTrack.coverUrl ? (
                                <img src={msg.sharedTrack.coverUrl} alt="Cover" className="w-full h-full object-cover grayscale" />
                              ) : (
                                <div className="w-full h-full bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center"><Music size={12} className="text-zinc-400 dark:text-zinc-500" /></div>
                              )}
                              <div className="absolute inset-0 flex items-center justify-center bg-black/40 opacity-0 hover:opacity-100 transition-opacity">
                                <Play size={14} className="text-white ml-0.5" />
                              </div>
                            </div>
                            <div className="overflow-hidden">
                              <p className={`text-[10px] font-black uppercase tracking-tight truncate ${isOwn ? 'text-white dark:text-white' : 'text-zinc-900 dark:text-white'}`}>
                                {msg.sharedTrack.title}
                                {msg.sharedTrackTimestamp != null && <span className="text-zinc-400 font-medium ml-1">[{formatAudioTime(msg.sharedTrackTimestamp)}]</span>}
                              </p>
                              <p className={`text-[9px] font-medium uppercase tracking-widest truncate ${isOwn ? 'text-zinc-400' : 'text-zinc-500 dark:text-zinc-400'}`}>{msg.sharedTrack.authorName}</p>
                            </div>
                          </div>
                        )}
                        {isBroadcastInit ? (
                          <div className={`px-5 py-3 transition-all flex flex-col gap-2 ${isOwn ? 'bg-zinc-900 text-white dark:bg-white dark:text-black' : 'bg-white dark:bg-zinc-900 text-zinc-900 dark:text-white border border-zinc-200 dark:border-zinc-800'}`}>
                            <span className="text-[10px] font-black uppercase tracking-widest">
                              {isTerminated ? 'Broadcast Terminated' : (isOwn ? 'Broadcast Node Initialized' : 'Broadcast Node Discovered')}
                            </span>
                            {isTerminated ? (
                              <div className="px-4 py-2 border border-current text-[9px] font-black uppercase tracking-widest flex items-center justify-center gap-2 opacity-50">
                                <X size={12} />
                                Inactive Node
                              </div>
                            ) : isOwn ? (
                              <div className="px-4 py-2 border border-current text-[9px] font-black uppercase tracking-widest flex items-center justify-center gap-2">
                                <Radio size={12} />
                                Broadcasting as Master
                              </div>
                            ) : (
                              <button 
                                onClick={() => joinBroadcast(broadcastId)}
                                disabled={broadcastStatus === 'SYNC' && activeBroadcastId === broadcastId}
                                className={`px-4 py-2 border border-current text-[9px] font-black uppercase tracking-widest flex items-center justify-center gap-2 transition-opacity ${broadcastStatus === 'SYNC' && activeBroadcastId === broadcastId ? 'opacity-50 cursor-not-allowed' : 'hover:opacity-70'}`}
                              >
                                <Radio size={12} />
                                {broadcastStatus === 'SYNC' && activeBroadcastId === broadcastId ? 'Connected' : 'Connect to Broadcast'}
                              </button>
                            )}
                          </div>
                        ) : msg.content ? (
                          <div className={`px-5 py-3 text-sm transition-all ${
                            isOwn 
                              ? 'bg-zinc-900 text-white dark:bg-white dark:text-black font-medium' 
                              : 'bg-white dark:bg-zinc-900 text-zinc-900 dark:text-white border border-zinc-200 dark:border-zinc-800'
                          }`}>
                            {msg.content}
                          </div>
                        ) : null}
                        {msg.fileUrl && (
                          <div className="mt-2">
                            <ChatAudioPlayer url={msg.fileUrl} id={msg.id} />
                          </div>
                        )}
                        <p className={`text-[9px] mt-2 font-medium text-zinc-400 dark:text-zinc-500 tabular-nums uppercase tracking-widest ${isOwn ? 'text-right' : 'text-left'}`}>
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
              <div className="p-8 bg-transparent border-t border-zinc-200 dark:border-zinc-800">
                <div className="border border-zinc-200 dark:border-zinc-800 p-6 bg-white dark:bg-zinc-900 text-center">
                  <p className="text-[10px] font-bold uppercase tracking-widest text-zinc-900 dark:text-white mb-6">
                    {activeChat.isGroup 
                      ? `You are invited to join Cluster: ${activeChat.name}.`
                      : `${getOtherParticipant(activeChat)?.displayName} wants to connect and send you transmissions.`}
                  </p>
                  <div className="flex justify-center gap-4">
                    <button 
                      onClick={() => useChatStore.getState().acceptChat(activeChat.id)}
                      className="px-8 py-3 bg-zinc-900 text-white dark:bg-white dark:text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-all"
                    >
                      {activeChat.isGroup ? 'Integrate' : 'Accept'}
                    </button>
                    <button 
                      onClick={() => useChatStore.getState().rejectChat(activeChat.id)}
                      className="px-8 py-3 border border-zinc-200 dark:border-zinc-800 text-zinc-900 dark:text-white text-[10px] font-black uppercase tracking-widest hover:border-zinc-900 dark:hover:border-white transition-all"
                    >
                      {activeChat.isGroup ? 'Sever Connection' : 'Decline'}
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              <div className="bg-transparent border-t border-zinc-200 dark:border-zinc-800 relative">
                {/* Active Attachment Preview */}
                {activeAttachment && (
                  <div className="absolute bottom-full left-0 right-0 p-3 bg-white/50 dark:bg-black/50 backdrop-blur-md border-t border-zinc-200 dark:border-zinc-800 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      {activeAttachment.coverUrl ? (
                        <img src={activeAttachment.coverUrl} className="w-8 h-8 object-cover grayscale" alt="attachment" />
                      ) : (
                        <div className="w-8 h-8 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center"><Music size={12} className="text-zinc-500" /></div>
                      )}
                      <div>
                        <p className="text-[10px] font-black uppercase tracking-tight text-zinc-900 dark:text-white">{activeAttachment.title}</p>
                        <p className="text-[9px] font-medium uppercase tracking-widest text-zinc-500 dark:text-zinc-400">{activeAttachment.authorName}</p>
                      </div>
                    </div>
                    <button onClick={clearAttachment} className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white p-1">
                      <X size={14} />
                    </button>
                  </div>
                )}
                <form 
                  onSubmit={handleSend}
                  className="flex items-center gap-4 border border-zinc-200 dark:border-zinc-800 p-1 bg-white dark:bg-zinc-900 m-8 mt-6"
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
                    className="p-3 text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white transition-colors disabled:opacity-50"
                  >
                    {uploading ? <Loader2 size={16} className="animate-spin" /> : <Paperclip size={16} />}
                  </button>
                  <input 
                    value={messageText}
                    onChange={(e) => setMessageText(e.target.value)}
                    placeholder="INPUT TRANSMISSION..." 
                    className="flex-1 bg-transparent px-2 py-3 text-[10px] font-bold uppercase tracking-widest focus:outline-none text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600"
                  />
                  <button 
                    type="submit"
                    disabled={!messageText.trim() && !activeAttachment}
                    className="px-6 py-3 bg-zinc-900 text-white dark:bg-white dark:text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-all disabled:opacity-30"
                  >
                    Send
                  </button>
                </form>
              </div>
            )}
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center text-zinc-500 dark:text-zinc-400 text-center p-12">
            <h3 className="text-xl font-black text-zinc-900 dark:text-white mb-4 uppercase tracking-tighter">Secure Messaging</h3>
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
          onCreateCluster={async (name, users) => {
            setIsNewChatModalOpen(false);
            await createCluster(name, users);
          }}
        />
      )}

      {isClusterDetailsOpen && activeChat?.isGroup && (
        <ClusterDetailsModal
          chatId={activeChat.id}
          clusterName={activeChat.name}
          onClose={() => setIsClusterDetailsOpen(false)}
        />
      )}
    </div>
  );
};

export default ChatPage;