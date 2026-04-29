import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Loader2, Music, Lock, Globe, Trash2, ArrowLeft, Plus, Search, GitFork, RefreshCw, X } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import useAuthStore from '../store/authStore';
import usePlayerStore from '../store/playerStore';
import ConfirmDialog from '../components/modals/ConfirmDialog';
import SyncConfirmModal from '../components/modals/SyncConfirmModal';

const PlaylistPage = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const { user } = useAuthStore();
  const { playTrack, currentTrack, isPlaying, togglePlayPause } = usePlayerStore();
  
  const [playlist, setPlaylist] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [isForking, setIsForking] = useState(false);

  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState([]);
  const [isSearching, setIsSearching] = useState(false);

  const [showConfirmDialog, setShowConfirmDialog] = useState(false);
  const [togglingVisibility, setTogglingVisibility] = useState(false);

  const [showSyncModal, setShowSyncModal] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [syncMessage, setSyncMessage] = useState(null);

  useEffect(() => {
    fetchPlaylist();
  }, [id]);

  useEffect(() => {
    if (!searchQuery.trim()) {
      setSearchResults([]);
      return;
    }
    
    const timeoutId = setTimeout(async () => {
      setIsSearching(true);
      try {
        const res = await axiosClient.get('/api/tracks/search', { params: { query: searchQuery, pageNo: 0, pageSize: 5 } });
        setSearchResults(res.data.content || []);
      } catch (err) {
        console.error("Failed to search tracks:", err);
      } finally {
        setIsSearching(false);
      }
    }, 500);

    return () => clearTimeout(timeoutId);
  }, [searchQuery]);

  const fetchPlaylist = async () => {
    setLoading(true);
    try {
      const res = await axiosClient.get(`/api/playlists/${id}`);
      setPlaylist(res.data);
    } catch (err) {
      console.error('Failed to fetch playlist', err);
      setError('You do not have access to this playlist or it does not exist.');
    } finally {
      setLoading(false);
    }
  };

  const handleFork = async () => {
    setIsForking(true);
    try {
      const res = await axiosClient.post(`/api/playlists/${id}/fork`);
      navigate(`/playlists/${res.data.id}`);
    } catch (err) {
      console.error('Failed to fork playlist', err);
    } finally {
      setIsForking(false);
    }
  };

  const handleSyncOrigin = async () => {
    setIsSyncing(true);
    try {
      const res = await axiosClient.post(`/api/playlists/${id}/sync-origin`);
      setPlaylist(res.data.playlist);
      setSyncMessage({ type: 'success', text: `Sync complete: +${res.data.newTracksAdded} new transmissions integrated` });
    } catch (err) {
      console.error('Failed to sync with origin', err);
      setSyncMessage({ type: 'error', text: 'Failed to sync with origin node.' });
    } finally {
      setIsSyncing(false);
      setShowSyncModal(false);
      setTimeout(() => setSyncMessage(null), 5000);
    }
  };

  const handleToggleVisibility = async () => {
    setTogglingVisibility(true);
    try {
      const res = await axiosClient.put(`/api/playlists/${id}/visibility`);
      setPlaylist(res.data);
    } catch (err) {
      console.error('Failed to toggle visibility', err);
    } finally {
      setTogglingVisibility(false);
      setShowConfirmDialog(false);
    }
  };

  const handleAddTrack = async (trackId) => {
    try {
      const res = await axiosClient.post(`/api/playlists/${id}/tracks/${trackId}`);
      setPlaylist(res.data);
      setSearchQuery('');
    } catch (err) {
      console.error('Failed to add track', err);
    }
  };

  const handleRemoveTrack = async (trackId) => {
    try {
      const res = await axiosClient.delete(`/api/playlists/${id}/tracks/${trackId}`);
      setPlaylist(res.data);
    } catch (err) {
      console.error('Failed to remove track', err);
    }
  };

  const handlePlayTrack = (track) => {
    if (currentTrack?.id === track.id) {
      togglePlayPause();
    } else {
      playTrack(track, playlist.tracks);
    }
  };

  const formatTime = (seconds) => {
    if (isNaN(seconds)) return "0:00";
    const m = Math.floor(seconds / 60);
    const s = seconds % 60;
    return `${m}:${s.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh]">
        <Loader2 size={32} className="text-muted animate-spin mb-4" />
        <p className="text-[10px] font-black uppercase tracking-[0.3em] text-muted">Accessing Playlist</p>
      </div>
    );
  }
  
  if (isForking) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh]">
        <Loader2 size={32} className="text-muted animate-spin mb-4" />
        <p className="text-[10px] font-black uppercase tracking-[0.3em] text-muted animate-pulse">Cloning data nodes...</p>
      </div>
    );
  }

  if (error || !playlist) {
    return (
      <div className="flex flex-col items-center justify-center h-[60vh] text-center border border-border border-dashed p-12">
        <Lock size={32} className="text-muted mb-4" />
        <h3 className="text-xs font-black uppercase tracking-[0.2em] mb-2">Access Denied</h3>
        <p className="text-[10px] text-muted uppercase tracking-widest">{error}</p>
        <button 
          onClick={() => navigate(-1)}
          className="mt-8 px-6 py-2 bg-white text-black text-[10px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-colors"
        >
          Return
        </button>
      </div>
    );
  }

  const isOwner = user?.userId === playlist.ownerId;

  return (
    <div className="space-y-16 pb-32 relative">
      {syncMessage && (
        <div className={`fixed top-8 left-1/2 -translate-x-1/2 z-[150] px-6 py-3 border text-[10px] font-black uppercase tracking-widest flex items-center gap-6 shadow-2xl animate-in slide-in-from-top-4 ${
          syncMessage.type === 'success' 
            ? 'border-white bg-black text-white' 
            : 'border-red-500 bg-black text-red-500'
        }`}>
          <span>{syncMessage.text}</span>
          <button onClick={() => setSyncMessage(null)} className="hover:opacity-50 transition-opacity">
            <X size={14} />
          </button>
        </div>
      )}

      <header className="flex flex-col md:flex-row items-end gap-8 pb-12 border-b border-border">
        <div className="w-48 h-48 flex-shrink-0 bg-surface border border-border">
          {playlist.coverUrl ? (
            <img src={playlist.coverUrl} alt="Cover" className="w-full h-full object-cover grayscale" />
          ) : (
            <div className="w-full h-full flex items-center justify-center"><Music size={32} className="text-muted" /></div>
          )}
        </div>
        
        <div className="flex-1 w-full space-y-4">
          <div className="flex items-center gap-3">
            <span className="px-2 py-0.5 bg-white text-black text-[9px] font-black uppercase tracking-widest">Playlist</span>
            <div className="flex items-center gap-1.5 px-2 py-0.5 border border-border text-[9px] font-black uppercase tracking-widest text-muted">
              {playlist.isPublic ? <Globe size={10} /> : <Lock size={10} />}
              {playlist.isPublic ? 'Public' : 'Private'}
            </div>
            {isOwner && (
              <button 
                onClick={() => setShowConfirmDialog(true)}
                className="px-2 py-0.5 border border-white/20 text-[9px] font-black uppercase tracking-widest text-muted hover:text-white hover:border-white transition-colors"
              >
                Toggle Privacy
              </button>
            )}
            {isOwner && playlist.forkedFromId && (
              <button 
                onClick={() => setShowSyncModal(true)}
                disabled={isSyncing}
                className="flex items-center gap-1.5 px-2 py-0.5 border border-white/20 text-[9px] font-black uppercase tracking-widest text-muted hover:text-white hover:border-white transition-colors"
              >
                {isSyncing ? <Loader2 size={10} className="animate-spin" /> : <RefreshCw size={10} />}
                Pull Updates
              </button>
            )}
            {!isOwner && playlist.isPublic && (
              <button 
                onClick={handleFork}
                disabled={isForking}
                className="flex items-center gap-1.5 px-2 py-0.5 bg-white text-black text-[9px] font-black uppercase tracking-widest hover:bg-zinc-200 transition-colors"
              >
                <GitFork size={10} />
                Fork Sequence
              </button>
            )}
          </div>
          
          <h1 className="text-5xl md:text-7xl font-black uppercase tracking-tighter truncate">{playlist.title}</h1>
          
          {playlist.forkedFromId && (
            <div className="mt-1">
              <button 
                onClick={() => navigate(`/playlists/${playlist.forkedFromId}`)}
                className="text-[10px] font-mono text-muted hover:text-white transition-colors text-left"
              >
                &lt;Fork Origin: {playlist.forkedFromName} // Source Identity: {playlist.forkedFromOwnerName}&gt;
              </button>
            </div>
          )}

          {playlist.description && (
            <p className="text-xs text-muted font-bold uppercase tracking-widest mt-2">{playlist.description}</p>
          )}

          <p className="text-[10px] font-bold uppercase tracking-widest text-muted">
             Tracks: {playlist.tracksCount}
          </p>
        </div>
      </header>

      <section>
        {playlist.tracks.length === 0 ? (
           <div className="border border-border border-dashed p-16 text-center">
             <p className="text-[10px] font-black uppercase tracking-widest text-muted">This playlist is empty.</p>
           </div>
        ) : (
          <div className="flex flex-col border border-border">
            <div className="flex items-center gap-4 px-6 py-3 border-b border-border bg-surface/50 text-[9px] font-black uppercase tracking-widest text-muted">
              <div className="w-8 text-center">#</div>
              <div className="flex-1">Title / Author</div>
              <div className="w-16 text-right">Time</div>
              {isOwner && <div className="w-10"></div>}
            </div>
            
            {playlist.tracks.map((track, idx) => (
              <div 
                key={track.id} 
                className="group flex items-center gap-4 px-6 py-4 hover:bg-white/5 transition-colors border-b border-border/50 last:border-b-0 cursor-pointer"
                onClick={() => handlePlayTrack(track)}
              >
                <div className="w-8 text-center text-[10px] font-bold text-muted group-hover:text-white transition-colors">
                  {currentTrack?.id === track.id && isPlaying ? (
                     <div className="w-3 h-3 bg-white mx-auto rounded-full animate-pulse" />
                  ) : (
                    (idx + 1).toString().padStart(2, '0')
                  )}
                </div>
                
                <div className="flex-1 flex items-center gap-4 overflow-hidden">
                   <div className="w-10 h-10 bg-zinc-800 flex-shrink-0">
                     {track.coverUrl ? (
                       <img src={track.coverUrl} className="w-full h-full object-cover grayscale" alt=""/>
                     ) : (
                       <div className="w-full h-full flex items-center justify-center"><Music size={12} className="text-muted" /></div>
                     )}
                   </div>
                   <div className="overflow-hidden">
                     <p className={`text-[11px] font-black uppercase tracking-tight truncate ${currentTrack?.id === track.id ? 'text-white' : 'text-zinc-300'}`}>
                       {track.title}
                     </p>
                     <p className="text-[9px] font-bold uppercase tracking-widest text-muted truncate">
                       {track.authorName}
                     </p>
                   </div>
                </div>

                <div className="w-16 text-right text-[10px] font-medium tabular-nums text-muted group-hover:text-white transition-colors">
                  {formatTime(track.duration)}
                </div>

                {isOwner && (
                  <div className="w-10 flex justify-end">
                    <button 
                      onClick={(e) => { e.stopPropagation(); handleRemoveTrack(track.id); }}
                      className="text-muted hover:text-red-500 opacity-0 group-hover:opacity-100 transition-all p-1"
                    >
                      <Trash2 size={14} />
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </section>

      {isOwner && (
        <section className="pt-12 border-t border-border">
          <h3 className="text-[10px] font-black uppercase tracking-[0.2em] text-muted mb-6">Append Tracks</h3>
          <div className="relative max-w-xl">
            <Search size={14} className="absolute left-4 top-1/2 -translate-y-1/2 text-muted" />
            <input 
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              placeholder="SEARCH CATALOG..."
              className="w-full bg-surface border border-border py-4 pl-12 pr-6 text-[10px] font-bold uppercase tracking-widest focus:outline-none focus:border-white transition-colors"
            />
          </div>

          {searchQuery.trim() && (
            <div className="mt-4 max-w-xl border border-border bg-surface">
              {isSearching ? (
                <div className="p-6 flex justify-center"><Loader2 size={16} className="animate-spin text-muted" /></div>
              ) : searchResults.length > 0 ? (
                searchResults.map(track => {
                  const isInPlaylist = playlist.tracks.some(t => t.id === track.id);
                  return (
                    <div key={track.id} className="flex items-center justify-between p-4 border-b border-border/50 last:border-b-0 hover:bg-white/5 transition-colors">
                      <div className="flex items-center gap-3 overflow-hidden">
                        <div className="w-8 h-8 bg-zinc-800 flex-shrink-0">
                          {track.coverUrl ? <img src={track.coverUrl} className="w-full h-full object-cover grayscale" alt=""/> : <Music size={12} className="text-muted" />}
                        </div>
                        <div className="overflow-hidden">
                          <p className="text-[10px] font-black uppercase tracking-tight text-white truncate">{track.title}</p>
                          <p className="text-[9px] font-bold uppercase tracking-widest text-muted truncate">{track.authorName}</p>
                        </div>
                      </div>
                      <button 
                        onClick={() => handleAddTrack(track.id)}
                        disabled={isInPlaylist}
                        className="px-3 py-1 bg-white text-black text-[9px] font-black uppercase tracking-widest disabled:opacity-30 disabled:bg-zinc-800 disabled:text-muted hover:bg-zinc-200 transition-colors flex items-center gap-1"
                      >
                        {isInPlaylist ? 'Added' : <><Plus size={10}/> Add</>}
                      </button>
                    </div>
                  );
                })
              ) : (
                <div className="p-6 text-center text-[10px] font-bold uppercase tracking-widest text-muted">No tracks found</div>
              )}
            </div>
          )}
        </section>
      )}

      <ConfirmDialog 
        isOpen={showConfirmDialog}
        title="Toggle Privacy"
        message={`Are you sure you want to make this playlist ${playlist?.isPublic ? 'Private' : 'Public'}?`}
        confirmText="Confirm"
        onConfirm={handleToggleVisibility}
        onCancel={() => setShowConfirmDialog(false)}
      />

      <SyncConfirmModal 
        isOpen={showSyncModal}
        originName={playlist?.forkedFromName}
        onConfirm={handleSyncOrigin}
        onCancel={() => setShowSyncModal(false)}
      />
    </div>
  );
};

export default PlaylistPage;