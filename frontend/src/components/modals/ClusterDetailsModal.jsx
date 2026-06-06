import React, { useState, useEffect } from 'react';
import { Search, X, Loader2, Network, Shield, Clock, Users, UserPlus, Check } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

const ClusterDetailsModal = ({ chatId, clusterName, onClose }) => {
  const [members, setMembers] = useState([]);
  const [loadingMembers, setLoadingMembers] = useState(true);
  
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [searching, setSearching] = useState(false);
  const [selectedUsers, setSelectedUsers] = useState([]);
  
  const [inviting, setInviting] = useState(false);

  const fetchMembers = async () => {
    try {
      setLoadingMembers(true);
      const res = await axiosClient.get(`/api/chats/${chatId}/members`);
      setMembers(res.data);
    } catch (error) {
      console.error("Failed to fetch members", error);
    } finally {
      setLoadingMembers(false);
    }
  };

  useEffect(() => {
    fetchMembers();
  }, [chatId]);

  useEffect(() => {
    const timer = setTimeout(async () => {
      if (!searchQuery.trim()) {
        setSearchResults([]);
        return;
      }
      try {
        setSearching(true);
        const res = await axiosClient.get('/api/users/search', { params: { q: searchQuery } });
        // Filter out users who are already members
        const allUsers = res.data.content || [];
        const filteredUsers = allUsers.filter(u => !members.some(m => m.userId === u.id));
        setSearchResults(filteredUsers);
      } catch (error) {
        console.error("User search failed", error);
      } finally {
        setSearching(false);
      }
    }, 300);

    return () => clearTimeout(timer);
  }, [searchQuery, members]);

  const toggleUserSelection = (user) => {
    if (selectedUsers.find(u => u.id === user.id)) {
      setSelectedUsers(selectedUsers.filter(u => u.id !== user.id));
    } else {
      setSelectedUsers([...selectedUsers, user]);
    }
  };

  const handleInvite = async () => {
    if (selectedUsers.length === 0) return;
    try {
      setInviting(true);
      await axiosClient.post(`/api/chats/${chatId}/members`, selectedUsers.map(u => u.id));
      setSelectedUsers([]);
      setSearchQuery('');
      setSearchResults([]);
      await fetchMembers();
    } catch (error) {
      console.error("Failed to invite nodes", error);
    } finally {
      setInviting(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-white/80 dark:bg-black/80 backdrop-blur-sm">
      <div className="w-full max-w-2xl bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
        
        {/* Header */}
        <div className="p-6 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between bg-zinc-50 dark:bg-zinc-900/80">
          <div className="flex items-center gap-3">
            <Network size={20} className="text-zinc-900 dark:text-white" />
            <h2 className="text-sm font-black uppercase tracking-[0.3em] text-zinc-900 dark:text-white">Cluster: {clusterName}</h2>
          </div>
          <button onClick={onClose} className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white transition-colors">
            <X size={20} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto flex flex-col md:flex-row bg-zinc-50 dark:bg-zinc-950">
          
          {/* Section 1: Active Nodes */}
          <div className="flex-1 border-b md:border-b-0 md:border-r border-zinc-200 dark:border-zinc-800 flex flex-col min-h-[300px]">
            <div className="p-4 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-100 dark:bg-white/5 flex items-center gap-2">
              <Users size={14} className="text-zinc-500 dark:text-zinc-400" />
              <h3 className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">Connected Nodes</h3>
            </div>
            <div className="flex-1 overflow-y-auto p-2">
              {loadingMembers ? (
                <div className="flex justify-center items-center h-full">
                  <Loader2 className="animate-spin text-zinc-400 dark:text-zinc-500" size={24} />
                </div>
              ) : members.length > 0 ? (
                <div className="space-y-1">
                  {members.map(member => (
                    <div key={member.userId} className="w-full p-4 flex items-center gap-4 bg-white dark:bg-zinc-900/50 border border-transparent hover:border-zinc-200 dark:hover:border-white/10 transition-all">
                      <div className="w-10 h-10 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] font-black text-zinc-900 dark:text-white shrink-0 relative overflow-hidden">
                        {member.avatarUrl ? (
                          <img src={member.avatarUrl} alt="" className="w-full h-full object-cover grayscale brightness-90" />
                        ) : (
                          member.displayName.charAt(0)
                        )}
                      </div>
                      <div className="flex-1 overflow-hidden">
                        <p className="text-[11px] font-black uppercase tracking-tight text-zinc-900 dark:text-white truncate">{member.displayName}</p>
                        <div className="flex items-center gap-2 mt-1">
                          {member.isAdmin && (
                            <span className="flex items-center gap-1 text-[8px] font-black uppercase tracking-widest text-zinc-900 dark:text-white bg-zinc-100 dark:bg-white/10 px-1.5 py-0.5 border border-zinc-200 dark:border-white/20">
                              <Shield size={8} /> Admin
                            </span>
                          )}
                          {member.status === 'PENDING' && (
                            <span className="flex items-center gap-1 text-[8px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400 bg-zinc-100 dark:bg-zinc-800 px-1.5 py-0.5 border border-zinc-200 dark:border-zinc-700">
                              <Clock size={8} /> Awaiting Integration
                            </span>
                          )}
                          {member.status === 'ACCEPTED' && !member.isAdmin && (
                            <span className="text-[8px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">Active</span>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flex justify-center items-center h-full">
                  <p className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">No nodes found.</p>
                </div>
              )}
            </div>
          </div>

          {/* Section 2: Invite Node */}
          <div className="flex-1 flex flex-col bg-zinc-50 dark:bg-zinc-950 min-h-[300px]">
            <div className="p-4 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-100 dark:bg-white/5 flex items-center gap-2">
              <UserPlus size={14} className="text-zinc-500 dark:text-zinc-400" />
              <h3 className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">Invite Node</h3>
            </div>
            
            <div className="p-4 border-b border-zinc-200 dark:border-zinc-800">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-400 dark:text-zinc-500" size={14} />
                <input 
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="SEARCH IDENTITIES..."
                  className="w-full bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 py-3 pl-10 pr-4 text-[10px] font-black uppercase tracking-widest focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-all text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600"
                />
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-2">
              {searching ? (
                <div className="flex justify-center items-center h-full">
                  <Loader2 className="animate-spin text-zinc-400 dark:text-zinc-500" size={24} />
                </div>
              ) : searchResults.length > 0 ? (
                <div className="space-y-1">
                  {searchResults.map((user) => {
                    const isSelected = selectedUsers.find(u => u.id === user.id);
                    return (
                      <button
                        key={user.id}
                        onClick={() => toggleUserSelection(user)}
                        className={`w-full p-3 flex items-center gap-3 transition-all text-left ${isSelected ? 'bg-zinc-100 border-zinc-200 dark:bg-white/10 border dark:border-white/20' : 'hover:bg-zinc-100 dark:hover:bg-white/[0.03] border border-transparent'}`}
                      >
                        <div className="w-8 h-8 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] font-black text-zinc-900 dark:text-white shrink-0">
                          {user.avatarUrl ? (
                            <img src={user.avatarUrl} alt="" className="w-full h-full object-cover grayscale brightness-90" />
                          ) : (
                            user.displayName.charAt(0)
                          )}
                        </div>
                        <div className="flex-1 overflow-hidden">
                          <p className="text-[10px] font-black uppercase tracking-tight text-zinc-900 dark:text-white truncate">{user.displayName}</p>
                        </div>
                        {isSelected && (
                          <Check size={14} className="text-zinc-900 dark:text-white shrink-0" />
                        )}
                      </button>
                    )
                  })}
                </div>
              ) : searchQuery ? (
                <div className="p-8 text-center">
                  <p className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">No available identities found.</p>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center h-full text-center p-8 opacity-50">
                  <Search size={24} className="text-zinc-400 dark:text-zinc-500 mb-2" />
                  <p className="text-[10px] font-black uppercase tracking-widest text-zinc-400 dark:text-zinc-500">Input query to find nodes</p>
                </div>
              )}
            </div>
            
            {/* Execute Invitation Button */}
            <div className="p-4 border-t border-zinc-200 dark:border-zinc-800 bg-white dark:bg-zinc-900">
              <button 
                onClick={handleInvite}
                disabled={selectedUsers.length === 0 || inviting}
                className="w-full py-4 bg-zinc-900 text-white dark:bg-white dark:text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-all disabled:opacity-30 flex items-center justify-center gap-2"
              >
                {inviting ? (
                  <>
                    <Loader2 size={14} className="animate-spin" /> Executing Protocol...
                  </>
                ) : (
                  `Execute Invitation (${selectedUsers.length})`
                )}
              </button>
            </div>
          </div>

        </div>
      </div>
    </div>
  );
};

export default ClusterDetailsModal;