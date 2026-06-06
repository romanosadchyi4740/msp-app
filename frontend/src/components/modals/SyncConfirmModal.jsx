import React from 'react';

const SyncConfirmModal = ({ isOpen, originName, onConfirm, onCancel, confirmText = "Execute Sync", cancelText = "Abort" }) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[200] flex items-center justify-center p-4">
      <div 
        className="absolute inset-0 bg-white/60 dark:bg-black/60 backdrop-blur-sm" 
        onClick={onCancel}
      ></div>
      
      <div className="relative w-full max-w-sm bg-white dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 shadow-2xl p-8 space-y-6">
        <h2 className="text-sm font-black uppercase tracking-widest text-zinc-900 dark:text-white">Initialize Data Sync</h2>
        <p className="text-xs text-zinc-500 dark:text-zinc-400 leading-relaxed">
          Pulling latest transmissions from origin node <span className="text-zinc-900 dark:text-white font-mono">[{originName}]</span>. This will append any new audio files to your local instance. Your existing data will not be overwritten.
        </p>
        
        <div className="flex items-center justify-end gap-4 pt-4 border-t border-zinc-200 dark:border-zinc-800">
          <button 
            onClick={onCancel}
            className="px-4 py-2 text-[10px] font-bold uppercase tracking-widest text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white transition-colors"
          >
            {cancelText}
          </button>
          <button 
            onClick={onConfirm}
            className="px-6 py-2 bg-zinc-900 text-white dark:bg-white dark:text-black text-[10px] font-black uppercase tracking-[0.2em] hover:bg-zinc-800 dark:hover:bg-zinc-200 transition-colors"
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  );
};

export default SyncConfirmModal;