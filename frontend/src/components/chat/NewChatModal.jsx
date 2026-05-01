import React, { useState, useEffect } from 'react';
import { Search, X, User as UserIcon, Loader2, Users, Network } from 'lucide-react';
import axiosClient from '../../api/axiosClient';

const NewChatModal = ({ onClose, onSelectUser, onCreateCluster }) => {
  const [step, setStep] = useState(1); // 1: Mode, 2A: P2P Search, 2B: Cluster Search, 2C: Cluster Config
  const [mode, setMode] = useState(null); // 'P2P' or 'CLUSTER'
  const [query, setQuery] = useState('');
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(false);
  const [selectedUsers, setSelectedUsers] = useState([]);
  const [clusterName, setClusterName] = useState('');

  useEffect(() => {
    const timer = setTimeout(() => {
      if (query.trim() && step !== 1 && step !== '2C') {
        searchUsers(query);
      } else {
        setUsers([]);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [query, step]);

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

  const toggleUserSelection = (user) => {
    if (selectedUsers.find(u => u.id === user.id)) {
      setSelectedUsers(selectedUsers.filter(u => u.id !== user.id));
    } else {
      setSelectedUsers([...selectedUsers, user]);
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

        {step === 1 && (
          <div className="p-8 flex flex-col gap-6 items-center justify-center">
            <p className="text-[10px] font-black uppercase tracking-[0.2em] text-muted mb-4">Select Connection Mode</p>
            <button 
              onClick={() => { setMode('P2P'); setStep('2A'); }}
              className="w-full p-6 border border-border hover:border-white bg-background/50 hover:bg-white/[0.03] transition-all flex flex-col items-center gap-3"
            >
              <UserIcon size={32} className="text-white" />
              <div className="text-center">
                <h3 className="text-xs font-black uppercase tracking-widest text-white">Establish Peer-to-Peer Link</h3>
                <p className="text-[9px] font-medium text-muted mt-2 tracking-widest">Secure 1-on-1 Transmission</p>
              </div>
            </button>

            <button 
              onClick={() => { setMode('CLUSTER'); setStep('2B'); }}
              className="w-full p-6 border border-border hover:border-white bg-background/50 hover:bg-white/[0.03] transition-all flex flex-col items-center gap-3"
            >
              <Network size={32} className="text-white" />
              <div className="text-center">
                <h3 className="text-xs font-black uppercase tracking-widest text-white">Initialize Cluster</h3>
                <p className="text-[9px] font-medium text-muted mt-2 tracking-widest">Multi-node Network Group</p>
              </div>
            </button>
          </div>
        )}

        {(step === '2A' || step === '2B') && (
          <>
            <div className="p-6 border-b border-border bg-background/50">
              <div className="flex items-center gap-4 mb-4">
                <button onClick={() => { setStep(1); setMode(null); setSelectedUsers([]); setQuery(''); }} className="text-[10px] font-black text-muted hover:text-white uppercase tracking-widest">
                  {'< Back'}
                </button>
                <span className="text-[10px] font-black uppercase tracking-widest text-white">
                  {mode === 'P2P' ? 'Target Node' : 'Select Nodes'}
                </span>
              </div>
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
              {step === '2B' && selectedUsers.length > 0 && (
                <div className="mt-4 flex flex-wrap gap-2">
                  {selectedUsers.map(u => (
                    <div key={u.id} className="flex items-center gap-2 bg-white/10 px-3 py-1.5 border border-white/20">
                      <span className="text-[9px] font-black uppercase tracking-widest text-white">{u.displayName}</span>
                      <button onClick={() => toggleUserSelection(u)} className="text-muted hover:text-white">
                        <X size={12} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-2">
              {loading ? (
                <div className="flex flex-col items-center justify-center p-12">
                  <Loader2 className="animate-spin text-muted" size={24} />
                </div>
              ) : users.length > 0 ? (
                <div className="space-y-1">
                  {users.map((user) => {
                    const isSelected = selectedUsers.find(u => u.id === user.id);
                    return (
                      <button
                        key={user.id}
                        onClick={() => {
                          if (step === '2A') {
                            onSelectUser(user.id);
                          } else {
                            toggleUserSelection(user);
                          }
                        }}
                        className={`w-full p-4 flex items-center gap-4 transition-all text-left group ${isSelected ? 'bg-white/10 border border-white/20' : 'hover:bg-white/[0.03]'}`}
                      >
                        <div className="w-10 h-10 bg-zinc-800 flex items-center justify-center text-[10px] font-black text-white">
                          {user.avatarUrl ? (
                            <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0" />
                          ) : user.displayName.charAt(0)}
                        </div>
                        <div>
                          <p className="text-[11px] font-black uppercase tracking-tight text-white">{user.displayName}</p>
                          <p className="text-[9px] font-bold uppercase tracking-widest text-muted">{user.role.replace('ROLE_', '')}</p>
                        </div>
                        {step === '2B' && isSelected && (
                          <div className="ml-auto text-[9px] font-black text-white uppercase tracking-widest">Selected</div>
                        )}
                      </button>
                    )
                  })}
                </div>
              ) : query ? (
                <div className="p-12 text-center">
                  <p className="text-[10px] font-black uppercase tracking-widest text-muted">No identities identified.</p>
                </div>
              ) : (
                <div className="p-12 text-center opacity-30">
                  <Users className="mx-auto mb-4" size={32} />
                  <p className="text-[10px] font-black uppercase tracking-widest">Awaiting input protocol.</p>
                </div>
              )}
            </div>

            {step === '2B' && (
              <div className="p-6 border-t border-border bg-surface">
                <button 
                  onClick={() => setStep('2C')}
                  disabled={selectedUsers.length === 0}
                  className="w-full py-4 bg-white text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-all disabled:opacity-30"
                >
                  Next Phase
                </button>
              </div>
            )}
          </>
        )}

        {step === '2C' && (
          <div className="p-8 flex flex-col h-full bg-background/50">
            <div className="flex items-center gap-4 mb-8">
              <button onClick={() => setStep('2B')} className="text-[10px] font-black text-muted hover:text-white uppercase tracking-widest">
                {'< Back'}
              </button>
              <span className="text-[10px] font-black uppercase tracking-widest text-white">Cluster Configuration</span>
            </div>
            
            <div className="flex-1 flex flex-col justify-center">
              <label className="text-[10px] font-black uppercase tracking-[0.2em] text-muted mb-4 text-center">Enter Cluster Designation</label>
              <input 
                autoFocus
                type="text"
                value={clusterName}
                onChange={(e) => setClusterName(e.target.value)}
                placeholder="CLUSTER_NAME"
                className="w-full bg-transparent border-b-2 border-border py-4 px-4 text-center text-lg font-black uppercase tracking-[0.3em] focus:outline-none focus:border-white transition-all text-white placeholder-zinc-800"
              />
            </div>

            <div className="mt-12">
              <button 
                onClick={() => onCreateCluster(clusterName, selectedUsers.map(u => u.id))}
                disabled={!clusterName.trim() || selectedUsers.length === 0}
                className="w-full py-4 bg-white text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-all disabled:opacity-30"
              >
                Initialize Cluster
              </button>
            </div>
          </div>
        )}

      </div>
    </div>
  );
};

export default NewChatModal;