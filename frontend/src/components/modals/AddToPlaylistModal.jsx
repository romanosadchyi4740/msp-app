import React, {useEffect, useState} from 'react';
import {Loader2, Music, Plus, X} from 'lucide-react';
import usePlaylistStore from '../../store/playlistStore';

const AddToPlaylistModal = () => {
  const { playlistModal, closePlaylistModal, myPlaylists, fetchMyPlaylists, addTrackToPlaylist, createPlaylist } = usePlaylistStore();
  const [isAdding, setIsAdding] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [newTitle, setNewTitle] = useState('');

  const { isOpen, track } = playlistModal;

  useEffect(() => {
    if (isOpen) {
      fetchMyPlaylists();
    }
  }, [isOpen, fetchMyPlaylists]);

  if (!isOpen || !track) return null;

  const handleAdd = async (playlistId) => {
    setIsAdding(true);
    const success = await addTrackToPlaylist(playlistId, track.id);
    if (success) {
      closePlaylistModal();
    }
    setIsAdding(false);
  };

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!newTitle.trim()) return;
    setIsCreating(true);
    const success = await createPlaylist(newTitle, '', false);
    if (success) {
      setNewTitle('');
      await fetchMyPlaylists();
    }
    setIsCreating(false);
  };

  return (
    <div className="fixed inset-0 z-[100] flex items-center justify-center p-4">
      <div 
        className="absolute inset-0 bg-black/60 backdrop-blur-sm" 
        onClick={closePlaylistModal}
      ></div>
      
      <div className="relative w-full max-w-md bg-surface border border-border shadow-2xl flex flex-col max-h-[85vh] overflow-hidden">
        <div className="p-6 border-b border-border flex items-center justify-between">
          <h2 className="text-sm font-black uppercase tracking-widest text-white">Add to Playlist</h2>
          <button onClick={closePlaylistModal} className="text-muted hover:text-white transition-colors">
            <X size={18} />
          </button>
        </div>

        <div className="p-6 bg-white/5 border-b border-border/50 flex items-center gap-4">
          <div className="w-12 h-12 flex-shrink-0 bg-zinc-800">
            {track.coverUrl ? (
              <img src={track.coverUrl} alt="cover" className="w-full h-full object-cover grayscale" />
            ) : (
              <div className="w-full h-full flex items-center justify-center"><Music size={16} className="text-muted" /></div>
            )}
          </div>
          <div className="flex-1 overflow-hidden">
            <p className="text-xs font-black uppercase tracking-tight text-white truncate">{track.title}</p>
            <p className="text-[10px] font-medium uppercase tracking-widest text-muted truncate">{track.authorName}</p>
          </div>
        </div>

        <div className="flex-1 overflow-y-auto p-6 space-y-8">
          <div>
            <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-muted mb-3">Your Collections</h3>
            {myPlaylists.length === 0 ? (
              <p className="text-xs text-muted">No playlists found.</p>
            ) : (
              <div className="space-y-2">
                {myPlaylists.map(playlist => (
                  <button
                    key={playlist.id}
                    onClick={() => handleAdd(playlist.id)}
                    disabled={isAdding}
                    className="w-full flex items-center justify-between p-3 bg-background border border-border hover:border-white/30 transition-all group"
                  >
                    <div className="flex items-center gap-3">
                      <div className="w-8 h-8 bg-zinc-800 flex items-center justify-center">
                        {playlist.coverUrl ? (
                           <img src={playlist.coverUrl} alt="" className="w-full h-full object-cover grayscale" />
                        ) : (
                           <Music size={12} className="text-muted" />
                        )}
                      </div>
                      <span className="text-[10px] font-bold uppercase tracking-wider text-white truncate">{playlist.title}</span>
                    </div>
                    <Plus size={14} className="text-muted opacity-0 group-hover:opacity-100 transition-opacity" />
                  </button>
                ))}
              </div>
            )}
          </div>

          <div>
             <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-muted mb-3">Create New</h3>
             <form onSubmit={handleCreate} className="flex gap-2">
               <input 
                 value={newTitle}
                 onChange={e => setNewTitle(e.target.value)}
                 placeholder="COLLECTION TITLE"
                 className="flex-1 bg-background border border-border px-4 py-2 text-[10px] font-bold uppercase tracking-widest focus:outline-none focus:border-white transition-colors"
               />
               <button 
                 disabled={isCreating || !newTitle.trim()}
                 className="px-4 bg-white text-black text-[10px] font-black uppercase tracking-widest disabled:opacity-50 hover:bg-zinc-200 transition-colors"
               >
                 {isCreating ? <Loader2 size={12} className="animate-spin" /> : 'Create'}
               </button>
             </form>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AddToPlaylistModal;