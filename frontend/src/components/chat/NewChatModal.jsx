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

  const toggleUserSelection = (user) => {
    if (selectedUsers.find(u => u.id === user.id)) {
      setSelectedUsers(selectedUsers.filter(u => u.id !== user.id));
    } else {
      setSelectedUsers([...selectedUsers, user]);
    }
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4 bg-white/80 dark:bg-black/80 backdrop-blur-sm">
      <div className="w-full max-w-lg bg-white dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 shadow-2xl overflow-hidden flex flex-col max-h-[80vh]">
        <div className="p-6 border-b border-zinc-200 dark:border-zinc-800 flex items-center justify-between bg-zinc-50 dark:bg-zinc-900">
          <h2 className="text-sm font-black uppercase tracking-[0.3em] text-zinc-900 dark:text-white">Identity Protocol</h2>
          <button onClick={onClose} className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white transition-colors">
            <X size={20} />
          </button>
        </div>

        {step === 1 && (
          <div className="p-8 flex flex-col gap-6 items-center justify-center bg-zinc-50 dark:bg-zinc-950">
            <p className="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400 mb-4">Select Connection Mode</p>
            <button 
              onClick={() => { setMode('P2P'); setStep('2A'); }}
              className="w-full p-6 border border-zinc-200 dark:border-zinc-800 hover:border-zinc-900 dark:hover:border-white bg-white dark:bg-zinc-900/50 hover:bg-zinc-100 dark:hover:bg-white/[0.03] transition-all flex flex-col items-center gap-3"
            >
              <UserIcon size={32} className="text-zinc-900 dark:text-white" />
              <div className="text-center">
                <h3 className="text-xs font-black uppercase tracking-widest text-zinc-900 dark:text-white">Establish Peer-to-Peer Link</h3>
                <p className="text-[9px] font-medium text-zinc-500 dark:text-zinc-400 mt-2 tracking-widest">Secure 1-on-1 Transmission</p>
              </div>
            </button>

            <button 
              onClick={() => { setMode('CLUSTER'); setStep('2B'); }}
              className="w-full p-6 border border-zinc-200 dark:border-zinc-800 hover:border-zinc-900 dark:hover:border-white bg-white dark:bg-zinc-900/50 hover:bg-zinc-100 dark:hover:bg-white/[0.03] transition-all flex flex-col items-center gap-3"
            >
              <Network size={32} className="text-zinc-900 dark:text-white" />
              <div className="text-center">
                <h3 className="text-xs font-black uppercase tracking-widest text-zinc-900 dark:text-white">Initialize Cluster</h3>
                <p className="text-[9px] font-medium text-zinc-500 dark:text-zinc-400 mt-2 tracking-widest">Multi-node Network Group</p>
              </div>
            </button>
          </div>
        )}

        {(step === '2A' || step === '2B') && (
          <>
            <div className="p-6 border-b border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-900/50">
              <div className="flex items-center gap-4 mb-4">
                <button onClick={() => { setStep(1); setMode(null); setSelectedUsers([]); setQuery(''); }} className="text-[10px] font-black text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white uppercase tracking-widest transition-colors">
                  {'< Back'}
                </button>
                <span className="text-[10px] font-black uppercase tracking-widest text-zinc-900 dark:text-white">
                  {mode === 'P2P' ? 'Target Node' : 'Select Nodes'}
                </span>
              </div>
              <div className="relative">
                <Search className="absolute left-4 top-1/2 -translate-y-1/2 text-zinc-400 dark:text-zinc-500" size={16} />
                <input 
                  autoFocus
                  type="text"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  placeholder="SEARCH IDENTITIES..."
                  className="w-full bg-white dark:bg-zinc-950 border border-zinc-200 dark:border-zinc-800 py-4 pl-12 pr-4 text-[10px] font-black uppercase tracking-widest focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-all text-zinc-900 dark:text-white placeholder-zinc-400 dark:placeholder-zinc-600"
                />
              </div>
              {step === '2B' && selectedUsers.length > 0 && (
                <div className="mt-4 flex flex-wrap gap-2">
                  {selectedUsers.map(u => (
                    <div key={u.id} className="flex items-center gap-2 bg-zinc-200 dark:bg-white/10 px-3 py-1.5 border border-zinc-300 dark:border-white/20">
                      <span className="text-[9px] font-black uppercase tracking-widest text-zinc-900 dark:text-white">{u.displayName}</span>
                      <button onClick={() => toggleUserSelection(u)} className="text-zinc-500 hover:text-zinc-900 dark:text-zinc-400 dark:hover:text-white transition-colors">
                        <X size={12} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>

            <div className="flex-1 overflow-y-auto p-2 bg-white dark:bg-zinc-950">
              {loading ? (
                <div className="flex flex-col items-center justify-center p-12">
                  <Loader2 className="animate-spin text-zinc-500 dark:text-zinc-400" size={24} />
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
                        className={`w-full p-4 flex items-center gap-4 transition-all text-left group ${isSelected ? 'bg-zinc-100 border-zinc-200 dark:bg-white/10 border dark:border-white/20' : 'hover:bg-zinc-50 dark:hover:bg-white/[0.03] border border-transparent'}`}
                      >
                        <div className="w-10 h-10 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] font-black text-zinc-900 dark:text-white">
                          {user.avatarUrl ? (
                            <img src={user.avatarUrl} alt="" referrerPolicy="no-referrer" className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0" />
                          ) : user.displayName.charAt(0)}
                        </div>
                        <div>
                          <p className="text-[11px] font-black uppercase tracking-tight text-zinc-900 dark:text-white">{user.displayName}</p>
                          <p className="text-[9px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400">{user.role.replace('ROLE_', '')}</p>
                        </div>
                        {step === '2B' && isSelected && (
                          <div className="ml-auto text-[9px] font-black text-zinc-900 dark:text-white uppercase tracking-widest">Selected</div>
                        )}
                      </button>
                    )
                  })}
                </div>
              ) : query ? (
                <div className="p-12 text-center">
                  <p className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-zinc-400">No identities identified.</p>
                </div>
              ) : (
                <div className="p-12 text-center opacity-50 dark:opacity-30">
                  <Users className="mx-auto mb-4 text-zinc-400 dark:text-white" size={32} />
                  <p className="text-[10px] font-black uppercase tracking-widest text-zinc-500 dark:text-white">Awaiting input protocol.</p>
                </div>
              )}
            </div>

            {step === '2B' && (
              <div className="p-6 border-t border-zinc-200 dark:border-zinc-800 bg-zinc-50 dark:bg-zinc-900">
                <button 
                  onClick={() => setStep('2C')}
                  disabled={selectedUsers.length === 0}
                  className="w-full py-4 bg-zinc-900 text-white dark:bg-white dark:text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-all disabled:opacity-30"
                >
                  Next Phase
                </button>
              </div>
            )}
          </>
        )}

        {step === '2C' && (
          <div className="p-8 flex flex-col h-full bg-white dark:bg-zinc-900/50">
            <div className="flex items-center gap-4 mb-8">
              <button onClick={() => setStep('2B')} className="text-[10px] font-black text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white uppercase tracking-widest transition-colors">
                {'< Back'}
              </button>
              <span className="text-[10px] font-black uppercase tracking-widest text-zinc-900 dark:text-white">Cluster Configuration</span>
            </div>
            
            <div className="flex-1 flex flex-col justify-center">
              <label className="text-[10px] font-black uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400 mb-4 text-center">Enter Cluster Designation</label>
              <input 
                autoFocus
                type="text"
                value={clusterName}
                onChange={(e) => setClusterName(e.target.value)}
                placeholder="CLUSTER_NAME"
                className="w-full bg-transparent border-b-2 border-zinc-200 dark:border-zinc-800 py-4 px-4 text-center text-lg font-black uppercase tracking-[0.3em] focus:outline-none focus:border-zinc-900 dark:focus:border-white transition-all text-zinc-900 dark:text-white placeholder-zinc-300 dark:placeholder-zinc-800"
              />
            </div>

            <div className="mt-12">
              <button 
                onClick={() => onCreateCluster(clusterName, selectedUsers.map(u => u.id))}
                disabled={!clusterName.trim() || selectedUsers.length === 0}
                className="w-full py-4 bg-zinc-900 text-white dark:bg-white dark:text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-all disabled:opacity-30"
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