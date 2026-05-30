import React, {useEffect, useState} from 'react';
import {useNavigate} from 'react-router-dom';
import {Library, Loader2, Music, Plus} from 'lucide-react';
import usePlaylistStore from '../store/playlistStore';

const PlaylistsPage = () => {
  const { myPlaylists, fetchMyPlaylists, loading, createPlaylist } = usePlaylistStore();
  const navigate = useNavigate();
  
  const [isCreating, setIsCreating] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDesc, setNewDesc] = useState('');

  useEffect(() => {
    fetchMyPlaylists();
  }, [fetchMyPlaylists]);

  const handleCreate = async (e) => {
    e.preventDefault();
    if (!newTitle.trim()) return;
    setIsCreating(true);
    const success = await createPlaylist(newTitle, newDesc, false);
    if (success) {
      setNewTitle('');
      setNewDesc('');
      await fetchMyPlaylists();
    }
    setIsCreating(false);
  };

  if (loading && myPlaylists.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh]">
        <Loader2 size={32} className="text-muted animate-spin mb-4" />
        <p className="text-[10px] font-black uppercase tracking-[0.3em] text-muted">Accessing Library</p>
      </div>
    );
  }

  return (
    <div className="space-y-16 pb-32">
      <header className="flex flex-col md:flex-row items-end gap-8 pb-12 border-b border-border">
        <div className="w-48 h-48 flex-shrink-0 bg-surface border border-border flex items-center justify-center">
          <Library size={64} className="text-muted" />
        </div>
        
        <div className="flex-1 w-full space-y-4">
          <div className="flex items-center gap-3">
            <span className="px-2 py-0.5 bg-white text-black text-[9px] font-black uppercase tracking-widest">Library</span>
          </div>
          
          <h1 className="text-5xl md:text-7xl font-black uppercase tracking-tighter truncate">Your Collections</h1>
          
          <p className="text-xs text-muted font-bold uppercase tracking-widest">
            Curated collections of audio transmissions.
          </p>

          <p className="text-[10px] font-bold uppercase tracking-widest text-muted">
             Total: {myPlaylists.length}
          </p>
        </div>
      </header>

      <section className="grid grid-cols-1 lg:grid-cols-3 gap-12">
        <div className="lg:col-span-2">
          {myPlaylists.length === 0 ? (
             <div className="border border-border border-dashed p-16 text-center">
               <p className="text-[10px] font-black uppercase tracking-widest text-muted">No playlists created yet.</p>
             </div>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 gap-6">
              {myPlaylists.map(playlist => (
                <button
                  key={playlist.id}
                  onClick={() => navigate(`/playlists/${playlist.id}`)}
                  className="group relative flex flex-col text-left transition-all duration-300"
                >
                  <div className="relative aspect-square w-full mb-3 overflow-hidden bg-surface border border-border group-hover:border-white/30 transition-colors">
                    {playlist.coverUrl ? (
                      <img 
                        src={playlist.coverUrl} 
                        alt={playlist.title} 
                        className="w-full h-full object-cover grayscale brightness-90 group-hover:grayscale-0 group-hover:brightness-100 group-hover:scale-105 transition-all duration-700"
                      />
                    ) : (
                      <div className="w-full h-full flex items-center justify-center">
                        <Music size={32} className="text-zinc-800" />
                      </div>
                    )}
                  </div>
                  <div className="space-y-1">
                    <h3 className="text-sm font-black uppercase tracking-tight text-white truncate">
                      {playlist.title}
                    </h3>
                    <p className="text-[10px] font-medium uppercase tracking-[0.2em] text-muted truncate">
                      {playlist.tracksCount || 0} Tracks
                    </p>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>

        {/* Create new playlist sidebar */}
        <div className="bg-surface p-8 border border-border h-fit">
          <h2 className="text-sm font-black uppercase tracking-widest text-white mb-6">Create New</h2>
          <form onSubmit={handleCreate} className="space-y-6">
            <div className="space-y-2">
              <label className="block text-[9px] font-black uppercase tracking-widest text-muted">Title</label>
              <input 
                required
                value={newTitle}
                onChange={e => setNewTitle(e.target.value)}
                placeholder="COLLECTION TITLE"
                className="w-full bg-background border border-border px-4 py-3 text-[10px] font-bold uppercase tracking-widest focus:outline-none focus:border-white transition-colors"
              />
            </div>
            <div className="space-y-2">
              <label className="block text-[9px] font-black uppercase tracking-widest text-muted">Description (Optional)</label>
              <textarea 
                value={newDesc}
                onChange={e => setNewDesc(e.target.value)}
                placeholder="DESCRIPTION"
                className="w-full bg-background border border-border px-4 py-3 text-[10px] font-bold uppercase tracking-widest focus:outline-none focus:border-white transition-colors resize-none h-24"
              />
            </div>
            <button 
              disabled={isCreating || !newTitle.trim()}
              className="w-full flex justify-center items-center gap-2 py-3 bg-white text-black text-[10px] font-black uppercase tracking-widest disabled:opacity-50 hover:bg-zinc-200 transition-colors"
            >
              {isCreating ? <Loader2 size={14} className="animate-spin" /> : <><Plus size={14}/> Create</>}
            </button>
          </form>
        </div>
      </section>
    </div>
  );
};

export default PlaylistsPage;
