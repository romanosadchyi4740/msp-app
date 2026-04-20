import React, { useState, useEffect } from 'react';
import { Search, X, User as UserIcon, Loader2 } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

const NewChatModal = ({ onClose, onSelectUser }) => {
  const [query, setQuery] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      if (query.trim()) {
        searchUsers(query);
      } else {
        setUsers([]);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  const searchUsers = async (searchTerm) => {
    setLoading(true);
    try {
      const response = await axiosClient.get('/api/users/search', { params: { q: searchTerm } });
      setUsers(response.data.content || []);
    } catch (error) {
      console.error('User search failed', error);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-background/80 backdrop-blur-sm">
      <div className="w-full max-w-lg bg-surface border border-border shadow-2xl overflow-hidden flex flex-col max-h-[80vh]">
        <div className="p-6 border-b border-border flex items-center justify-between">
          <h2 className="text-sm font-black uppercase tracking-[0.3em]">Identity Protocol</h2>
          <button onClick={onClose} className="text-muted hover:text-white transition-colors">
            <X size={20} />
          </button>
        </div>

        <div className="p-6 border-b border-border">
          <div className="relative">
            <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-muted" size={16} />
            <input 
              autoFocus
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="SEARCH IDENTITIES..."
              className="w-full bg-background border border-border py-4 pl-12 pr-4 text-[10px] font-black uppercase tracking-widest focus:outline-none focus:border-white transition-all"
            />
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-2">
          {loading ? (
            <div className="flex flex-col items-center justify-center p-12">
              <Loader2 className="animate-spin text-muted" size={24} />
            </div>
          ) : users.length > 0 ? (
            <div className="space-y-1">
              {users.map((user) => (
                <button
                  key={user.id}
                  onClick={() => onSelectUser(user.id)}
                  className="w-full p-4 flex items-center gap-4 hover:bg-white/[0.03] transition-all text-left group"
                >
                  <div className="w-10 h-10 bg-zinc-800 flex items-center justify-center text-[10px] font-black text-white">
                    {user.avatarUrl ? (
                      <img src={user.avatarUrl} alt="" className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0" />
                    ) : user.displayName.charAt(0)}
                  </div>
                  <div>
                    <p className="text-[11px] font-black uppercase tracking-tight text-white">{user.displayName}</p>
                    <p className="text-[9px] font-bold uppercase tracking-widest text-muted">{user.role.replace('ROLE_', '')}</p>
                  </div>
                </button>
              ))}
            </div>
          ) : query ? (
            <div className="p-12 text-center">
              <p className="text-[10px] font-black uppercase tracking-widest text-muted">No identities identified.</p>
            </div>
          ) : (
            <div className="p-12 text-center opacity-30">
              <UserIcon className="mx-auto mb-4" size={32} />
              <p className="text-[10px] font-black uppercase tracking-widest">Awaiting input protocol.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default NewChatModal;
