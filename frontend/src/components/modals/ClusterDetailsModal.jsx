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
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-background/80 backdrop-blur-sm">
      <div className="w-full max-w-2xl bg-surface border border-border shadow-2xl overflow-hidden flex flex-col max-h-[85vh]">
        
        {/* Header */}
        <div className="p-6 border-b border-border flex items-center justify-between bg-surface/80">
          <div className="flex items-center gap-3">
            <Network size={20} className="text-white" />
            <h2 className="text-sm font-black uppercase tracking-[0.3em] text-white">Cluster: {clusterName}</h2>
          </div>
          <button onClick={onClose} className="text-muted hover:text-white transition-colors">
            <X size={20} />
          </button>
        </div>

        <div className="flex-1 overflow-y-auto flex flex-col md:flex-row">
          
          {/* Section 1: Active Nodes */}
          <div className="flex-1 border-b md:border-b-0 md:border-r border-border flex flex-col min-h-[300px]">
            <div className="p-4 border-b border-border bg-white/5 flex items-center gap-2">
              <Users size={14} className="text-muted" />
              <h3 className="text-[10px] font-black uppercase tracking-widest text-muted">Connected Nodes</h3>
            </div>
            <div className="flex-1 overflow-y-auto p-2">
              {loadingMembers ? (
                <div className="flex justify-center items-center h-full">
                  <Loader2 className="animate-spin text-muted" size={24} />
                </div>
              ) : members.length > 0 ? (
                <div className="space-y-1">
                  {members.map(member => (
                    <div key={member.userId} className="w-full p-4 flex items-center gap-4 bg-background/50 border border-transparent hover:border-white/10 transition-all">
                      <div className="w-10 h-10 bg-zinc-800 flex items-center justify-center text-[10px] font-black text-white shrink-0 relative overflow-hidden">
                        {member.avatarUrl ? (
                          <img src={member.avatarUrl} alt="" className="w-full h-full object-cover grayscale brightness-90" />
                        ) : (
                          member.displayName.charAt(0)
                        )}
                      </div>
                      <div className="flex-1 overflow-hidden">
                        <p className="text-[11px] font-black uppercase tracking-tight text-white truncate">{member.displayName}</p>
                        <div className="flex items-center gap-2 mt-1">
                          {member.isAdmin && (
                            <span className="flex items-center gap-1 text-[8px] font-black uppercase tracking-widest text-white bg-white/10 px-1.5 py-0.5 border border-white/20">
                              <Shield size={8} /> Admin
                            </span>
                          )}
                          {member.status === 'PENDING' && (
                            <span className="flex items-center gap-1 text-[8px] font-black uppercase tracking-widest text-zinc-400 bg-zinc-800 px-1.5 py-0.5 border border-zinc-700">
                              <Clock size={8} /> Awaiting Integration
                            </span>
                          )}
                          {member.status === 'ACCEPTED' && !member.isAdmin && (
                            <span className="text-[8px] font-black uppercase tracking-widest text-muted">Active</span>
                          )}
                        </div>
                      </div>
                    </div>
                  ))}
                </div>
              ) : (
                <div className="flex justify-center items-center h-full">
                  <p className="text-[10px] font-black uppercase tracking-widest text-muted">No nodes found.</p>
                </div>
              )}
            </div>
          </div>

          {/* Section 2: Invite Node */}
          <div className="flex-1 flex flex-col bg-background/30 min-h-[300px]">
            <div className="p-4 border-b border-border bg-white/5 flex items-center gap-2">
              <UserPlus size={14} className="text-muted" />
              <h3 className="text-[10px] font-black uppercase tracking-widest text-muted">Invite Node</h3>
            </div>
            
            <div className="p-4 border-b border-border">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-muted" size={14} />
                <input 
                  type="text"
                  value={searchQuery}
                  onChange={(e) => setSearchQuery(e.target.value)}
                  placeholder="SEARCH IDENTITIES..."
                  className="w-full bg-background border border-border py-3 pl-10 pr-4 text-[10px] font-black uppercase tracking-widest focus:outline-none focus:border-white transition-all text-white"
                />
              </div>
            </div>

            <div className="flex-1 overflow-y-auto p-2">
              {searching ? (
                <div className="flex justify-center items-center h-full">
                  <Loader2 className="animate-spin text-muted" size={24} />
                </div>
              ) : searchResults.length > 0 ? (
                <div className="space-y-1">
                  {searchResults.map((user) => {
                    const isSelected = selectedUsers.find(u => u.id === user.id);
                    return (
                      <button
                        key={user.id}
                        onClick={() => toggleUserSelection(user)}
                        className={`w-full p-3 flex items-center gap-3 transition-all text-left ${isSelected ? 'bg-white/10 border border-white/20' : 'hover:bg-white/[0.03] border border-transparent'}`}
                      >
                        <div className="w-8 h-8 bg-zinc-800 flex items-center justify-center text-[10px] font-black text-white shrink-0">
                          {user.avatarUrl ? (
                            <img src={user.avatarUrl} alt="" className="w-full h-full object-cover grayscale brightness-90" />
                          ) : (
                            user.displayName.charAt(0)
                          )}
                        </div>
                        <div className="flex-1 overflow-hidden">
                          <p className="text-[10px] font-black uppercase tracking-tight text-white truncate">{user.displayName}</p>
                        </div>
                        {isSelected && (
                          <Check size={14} className="text-white shrink-0" />
                        )}
                      </button>
                    )
                  })}
                </div>
              ) : searchQuery ? (
                <div className="p-8 text-center">
                  <p className="text-[10px] font-black uppercase tracking-widest text-muted">No available identities found.</p>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center h-full text-center p-8 opacity-50">
                  <Search size={24} className="text-muted mb-2" />
                  <p className="text-[10px] font-black uppercase tracking-widest text-muted">Input query to find nodes</p>
                </div>
              )}
            </div>
            
            {/* Execute Invitation Button */}
            <div className="p-4 border-t border-border bg-surface">
              <button 
                onClick={handleInvite}
                disabled={selectedUsers.length === 0 || inviting}
                className="w-full py-4 bg-white text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-all disabled:opacity-30 flex items-center justify-center gap-2"
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