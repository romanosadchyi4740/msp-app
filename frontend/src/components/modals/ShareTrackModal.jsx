import React, { useState, useEffect } from 'react';
import { X, Search, Loader2, Music } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import useChatStore from '../../store/chatStore';
import useAuthStore from '../../store/authStore';
import axiosClient from '../../api/axiosClient';

const ShareTrackModal = () => {
  const { shareModal, closeShareModal, setAttachment, chats, startChatWithUser } = useChatStore();
  const { user: currentUser } = useAuthStore();
  const navigate = useNavigate();
  
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);
  const [isSending, setIsSending] = useState(false);

  const { isOpen, track, timestamp } = shareModal;

  // Debounced search
  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults([]);
      return;
    }
    
    const timeoutId = setTimeout(async () => {
      setIsSearching(true);
      try {
        const res = await axiosClient.get('/api/users/search', { params: { q: searchQuery, size: 5 } });
        setSearchResults(res.data.content || []);
      } catch (err) {
        console.error("Failed to search users:", err);
      } finally {
        setIsSearching(false);
      }
    }, 500);

    return () => clearTimeout(timeoutId);
  }, [searchQuery]);

  if (!isOpen || !track) return null;

  const handleShare = async (recipientId) => {
    setIsSending(true);
    try {
      await startChatWithUser(recipientId);
      setAttachment({ ...track, shareTimestamp: timestamp });
      closeShareModal();
      navigate('/chat');
    } catch (err) {
      console.error("Failed to start chat for sharing:", err);
    } finally {
      setIsSending(false);
    }
  };

  const formatAudioTime = (time) => {
    if (isNaN(time) || time === null) return "0:00";
    const minutes = Math.floor(time / 60);
    const seconds = Math.floor(time % 60);
    return `${minutes}:${seconds.toString().padStart(2, '0')}`;
  };

  // Extract unique other participants from recent chats
  const recentContactsMap = new Map();
  chats.forEach(chat => {
    const otherUser = chat.participants.find(p => p.id !== currentUser?.userId);
    if (otherUser && !recentContactsMap.has(otherUser.id)) {
      recentContactsMap.set(otherUser.id, otherUser);
    }
  });
  
  // Exclude the author from recent contacts list if they are in it, 
  // because we already have a dedicated "Send to Author" button.
  if (recentContactsMap.has(track.authorId)) {
    recentContactsMap.delete(track.authorId);
  }

  const recentContacts = Array.from(recentContactsMap.values()).slice(0, 4);

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
      <div 
        className="absolute inset-0 bg-white/60 dark:bg-black/60 backdrop-blur-sm" 
        onClick={closeShareModal}
      ></div>
      
      <div className="relative w-full max-w-md bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
        {/* Header */}
        <div className="p-6 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between bg-zinc-50 dark:bg-zinc-900">
          <h2 className="text-sm font-black uppercase tracking-widest text-zinc-900 dark:text-white">Share Transmission</h2>
          <button onClick={closeShareModal} className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white transition-colors">
            <X size={18} />
          </button>
        </div>

        {/* Track Preview */}
        <div className="p-6 bg-zinc-100 dark:bg-white/5 border-b border-zinc-200 dark:border-zinc-800/50 flex items-center gap-4">
          <div className="w-12 h-12 flex-shrink-0 bg-zinc-200 dark:bg-zinc-800">
            {track.coverUrl ? (
              <img src={track.coverUrl} alt="cover" className="w-full h-full object-cover grayscale" />
            ) : (
              <div className="w-full h-full flex items-center justify-center"><Music size={16} className="text-zinc-400 dark:text-zinc-500" /></div>
            )}
          </div>
          <div className="flex-1 overflow-hidden">
            <p className="text-xs font-black uppercase tracking-tight text-zinc-900 dark:text-white truncate">{track.title}</p>
            <p className="text-[10px] font-medium uppercase tracking-widest text-zinc-500 dark:text-zinc-400 truncate">{track.authorName}</p>
          </div>
          {timestamp != null && (
            <div className="px-3 py-1 bg-zinc-200 dark:bg-zinc-800 text-[10px] font-black tabular-nums text-zinc-900 dark:text-white border border-zinc-300 dark:border-white/10 rounded-sm">
              {formatAudioTime(timestamp)}
            </div>
          )}
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-8 bg-zinc-50 dark:bg-zinc-950">
          {/* Send to Author */}
          {track.authorId !== currentUser?.userId && (
            <div>
              <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400 mb-3">Direct to Author</h3>
              <button
                onClick={() => handleShare(track.authorId)}
                disabled={isSending}
                className="w-full flex items-center justify-between p-4 bg-zinc-100 dark:bg-white/5 border border-zinc-300 dark:border-white/10 hover:border-zinc-900 dark:hover:border-white/30 transition-all group"
              >
                <div className="flex items-center gap-3">
                  <div className="w-8 h-8 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] font-black text-zinc-900 dark:text-white group-hover:bg-zinc-300 dark:group-hover:bg-zinc-700 transition-colors">
                    {track.authorName?.charAt(0)}
                  </div>
                  <span className="text-[11px] font-black uppercase tracking-widest text-zinc-900 dark:text-white">{track.authorName}</span>
                </div>
                <span className="text-[9px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400 group-hover:text-zinc-900 dark:group-hover:text-white transition-colors">Send</span>
              </button>
            </div>
          )}

          {/* Search */}
          <div>
            <div className="relative">
              <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400 dark:text-zinc-500" size={14} />
              <input
                type="text"
                placeholder="SEARCH RECIPIENTS..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 py-3 pl-10 pr-4 text-[10px] font-bold uppercase tracking-widest focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-all text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600"
              />
            </div>

            {/* Search Results */}
            {searchQuery.trim() && (
              <div className="mt-2 space-y-1">
                {isSearching ? (
                  <div className="p-4 flex items-center justify-center">
                    <Loader2 size={16} className="animate-spin text-zinc-400 dark:text-zinc-500" />
                  </div>
                ) : searchResults.length > 0 ? (
                  searchResults.filter(u => u.id !== currentUser?.userId).map(user => (
                    <button
                      key={user.id}
                      onClick={() => handleShare(user.id)}
                      disabled={isSending}
                      className="w-full flex items-center justify-between p-3 hover:bg-zinc-100 dark:hover:bg-white/5 transition-colors group border border-transparent hover:border-zinc-200 dark:hover:border-zinc-800"
                    >
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] font-black text-zinc-900 dark:text-white">
                          {user.displayName?.charAt(0)}
                        </div>
                        <span className="text-[11px] font-bold uppercase tracking-wider text-zinc-900 dark:text-white">{user.displayName}</span>
                      </div>
                      <span className="text-[9px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400 opacity-0 group-hover:opacity-100 transition-opacity">Select</span>
                    </button>
                  ))
                ) : (
                  <div className="p-4 text-center text-[10px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400">
                    No users found
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Recent Contacts */}
          {!searchQuery.trim() && recentContacts.length > 0 && (
            <div>
              <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400 mb-3">Recent Connections</h3>
              <div className="grid grid-cols-2 gap-2">
                {recentContacts.map(contact => (
                  <button
                    key={contact.id}
                    onClick={() => handleShare(contact.id)}
                    disabled={isSending}
                    className="flex items-center gap-3 p-3 bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 hover:border-zinc-900 dark:hover:border-white/30 transition-all group"
                  >
                    <div className="w-8 h-8 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] font-black text-zinc-900 dark:text-white">
                      {contact.displayName?.charAt(0)}
                    </div>
                    <span className="text-[10px] font-bold uppercase tracking-wider text-zinc-900 dark:text-white truncate">{contact.displayName}</span>
                  </button>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default ShareTrackModal;