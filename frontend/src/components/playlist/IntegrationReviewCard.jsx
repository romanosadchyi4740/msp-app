import React, { useState } from 'react';
import { User, Check, X, Loader2, GitMerge } from 'lucide-react';
import axiosClient from '../../api/axiosClient';
import toast from 'react-hot-toast';

const IntegrationReviewCard = ({ request, onResolve }) => {
  const [resolving, setResolving] = useState(false);

  const handleResolve = async (action) => {
    setResolving(true);
    try {
      await axiosClient.post(`/api/integrations/${request.id}/resolve?action=${action}`);
      toast.success(action === 'ACCEPT' 
        ? 'MERGE_EXECUTED: Node synchronized.' 
        : 'TRANSMISSION_REJECTED: Delta ignored.');
      onResolve(request.id, action);
    } catch (error) {
      console.error('Failed to resolve integration', error);
      toast.error('RESOLUTION_FAILED: Protocol error.');
    } finally {
      setResolving(false);
    }
  };

  return (
    <div className="border border-border bg-surface/30 p-6 space-y-6 animate-in slide-in-from-top-4">
      <div className="flex items-center justify-between border-b border-border/50 pb-4">
        <div className="flex items-center gap-4">
          <div className="w-8 h-8 bg-zinc-800 flex items-center justify-center flex-shrink-0">
            {request.requester.avatarUrl ? (
              <img src={request.requester.avatarUrl} className="w-full h-full object-cover grayscale" alt="" />
            ) : (
              <User size={12} className="text-muted" />
            )}
          </div>
          <div>
            <h4 className="text-[10px] font-black uppercase tracking-widest text-white">{request.requester.displayName}</h4>
            <p className="text-[9px] font-bold uppercase tracking-widest text-muted">Proposes Integration</p>
          </div>
        </div>
        <div className="text-[9px] font-mono text-zinc-500">
          Delta: +{request.proposedTracks.length} Transmissions
        </div>
      </div>

      <div className="space-y-2">
        <p className="text-[9px] font-black uppercase tracking-[0.2em] text-muted">Proposed Data Delta:</p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-2">
          {request.proposedTracks.map(track => (
            <div key={track.id} className="flex items-center gap-3 p-2 bg-black/20 border border-white/5">
              <span className="text-[10px] font-black text-green-500">+</span>
              <div className="overflow-hidden">
                <p className="text-[10px] font-black uppercase tracking-tight text-white truncate">{track.title}</p>
                <p className="text-[9px] font-bold uppercase tracking-widest text-muted truncate">{track.authorName}</p>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="flex items-center gap-4 pt-2">
        <button 
          onClick={() => handleResolve('ACCEPT')}
          disabled={resolving}
          className="flex-1 py-3 bg-white text-black text-[9px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
        >
          {resolving ? <Loader2 size={12} className="animate-spin" /> : <><Check size={12} /> Execute Merge</>}
        </button>
        <button 
          onClick={() => handleResolve('REJECT')}
          disabled={resolving}
          className="flex-1 py-3 border border-red-900/50 text-red-500 text-[9px] font-black uppercase tracking-widest hover:bg-red-500/10 transition-colors disabled:opacity-50 flex items-center justify-center gap-2"
        >
          {resolving ? <Loader2 size={12} className="animate-spin" /> : <><X size={12} /> Reject Transmission</>}
        </button>
      </div>
    </div>
  );
};

export default IntegrationReviewCard;