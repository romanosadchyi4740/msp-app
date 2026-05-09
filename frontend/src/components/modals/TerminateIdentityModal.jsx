import React, { useState } from 'react';
import { AlertTriangle, X, Loader2 } from 'lucide-react';
import axiosClient from '../../api/axiosClient';
import useAuthStore from '../../store/authStore';
import toast from 'react-hot-toast';
import { useNavigate } from 'react-router-dom';

const TerminateIdentityModal = ({ onClose }) => {
  const [confirmationText, setConfirmationText] = useState('');
  const [isTerminating, setIsTerminating] = useState(false);
  const logout = useAuthStore(state => state.logout);
  const navigate = useNavigate();

  const handleTerminate = async () => {
    if (confirmationText !== 'TERMINATE') return;

    setIsTerminating(true);
    try {
      await axiosClient.delete('/api/users/me');
      toast.success('IDENTITY_TERMINATED: Node purged from global network.', {
        duration: 5000,
        icon: 'тШв',
      });
      logout();
      navigate('/login');
    } catch (error) {
      console.error('Termination failed', error);
      toast.error('TERMINATION_FAILED: Protocol error during purge.');
      setIsTerminating(false);
    }
  };

  return (
    <div className="fixed inset-0 z-[110] flex items-center justify-center p-4 bg-black/90 backdrop-blur-md">
      <div className="w-full max-w-md bg-zinc-950 border border-red-900/50 shadow-[0_0_50px_rgba(153,27,27,0.2)] overflow-hidden">
        <div className="p-6 border-b border-red-900/30 flex items-center justify-between bg-red-950/10">
          <div className="flex items-center gap-3 text-red-500">
            <AlertTriangle size={20} />
            <h2 className="text-sm font-black uppercase tracking-[0.3em]">Critical Protocol</h2>
          </div>
          <button onClick={onClose} className="text-zinc-600 hover:text-white transition-colors">
            <X size={20} />
          </button>
        </div>

        <div className="p-8 space-y-8">
          <div className="space-y-4">
            <h3 className="text-lg font-black uppercase tracking-tight text-white">Warning: Identity Termination</h3>
            <p className="text-[10px] font-bold uppercase tracking-widest text-zinc-400 leading-relaxed">
              This action is irreversible. All your uploaded audio transmissions (tracks) and curated collections (playlists) will be permanently purged. Your chat messages will remain but will be anonymized.
            </p>
          </div>

          <div className="space-y-4">
            <label className="block text-[9px] font-black uppercase tracking-[0.2em] text-red-500/70">
              Type 'TERMINATE' to confirm deletion
            </label>
            <input 
              autoFocus
              type="text"
              value={confirmationText}
              onChange={(e) => setConfirmationText(e.target.value)}
              placeholder="CONFIRMATION_STRING"
              className="w-full bg-black border border-red-900/30 p-4 text-xs font-black uppercase tracking-widest focus:outline-none focus:border-red-500 transition-all text-red-500 placeholder-red-950"
            />
          </div>

          <div className="flex flex-col gap-3">
            <button 
              onClick={handleTerminate}
              disabled={confirmationText !== 'TERMINATE' || isTerminating}
              className="w-full py-4 bg-red-600 text-white text-[10px] font-black uppercase tracking-[0.2em] hover:bg-red-500 transition-all disabled:opacity-20 disabled:grayscale flex items-center justify-center gap-3"
            >
              {isTerminating ? <Loader2 size={16} className="animate-spin" /> : 'Execute Purge'}
            </button>
            <button 
              onClick={onClose}
              disabled={isTerminating}
              className="w-full py-4 bg-transparent border border-zinc-800 text-zinc-500 text-[10px] font-black uppercase tracking-[0.2em] hover:text-white hover:border-white transition-all disabled:opacity-50"
            >
              Abort
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TerminateIdentityModal;