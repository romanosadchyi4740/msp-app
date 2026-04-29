import React from 'react';

const SyncConfirmModal = ({ isOpen, originName, onConfirm, onCancel, confirmText = "Execute Sync", cancelText = "Abort" }) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
      <div 
        className="absolute inset-0 bg-black/60 backdrop-blur-sm" 
        onClick={onCancel}
      ></div>
      
      <div className="relative w-full max-w-sm bg-surface border border-border shadow-2xl p-8 space-y-6">
        <h2 className="text-sm font-black uppercase tracking-widest text-white">Initialize Data Sync</h2>
        <p className="text-xs text-muted leading-relaxed">
          Pulling latest transmissions from origin node <span className="text-white font-mono">[{originName}]</span>. This will append any new audio files to your local instance. Your existing data will not be overwritten.
        </p>
        
        <div className="flex items-center justify-end gap-4 pt-4 border-t border-border">
          <button 
            onClick={onCancel}
            className="px-4 py-2 text-[10px] font-bold uppercase tracking-widest text-muted hover:text-white transition-colors"
          >
            {cancelText}
          </button>
          <button 
            onClick={onConfirm}
            className="px-6 py-2 bg-white text-black text-[10px] font-black uppercase tracking-[0.2em] hover:bg-zinc-200 transition-colors"
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
};

export default SyncConfirmModal;