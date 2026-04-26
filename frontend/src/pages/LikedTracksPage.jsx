import React, { useState, useEffect } from 'react';
import { Loader2, Music, Heart } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import usePlayerStore from '../store/playerStore';

const LikedTracksPage = () => {
  const { playTrack, currentTrack, isPlaying, togglePlayPause } = usePlayerStore();
  const [tracks, setTracks] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchLikedTracks();
  }, []);

  const fetchLikedTracks = async () => {
    setLoading(true);
    try {
      const res = await axiosClient.get('/api/tracks/liked');
      setTracks(res.data || []);
    } catch (err) {
      console.error('Failed to fetch liked tracks', err);
    } finally {
      setLoading(false);
    }
  };

  const handlePlayTrack = (track) => {
    if (currentTrack?.id === track.id) {
      togglePlayPause();
    } else {
      playTrack(track, tracks);
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
        <p className="text-[10px] font-black uppercase tracking-[0.3em] text-muted">Accessing Library</p>
      </div>
    );
  }

  return (
    <div className="space-y-16 pb-32">
      <header className="flex flex-col md:flex-row items-end gap-8 pb-12 border-b border-border">
        <div className="w-48 h-48 flex-shrink-0 bg-white border border-border flex items-center justify-center">
          <Heart size={64} className="text-black" fill="currentColor" />
        </div>
        
        <div className="flex-1 w-full space-y-4">
          <div className="flex items-center gap-3">
            <span className="px-2 py-0.5 bg-white text-black text-[9px] font-black uppercase tracking-widest">System Collection</span>
            <div className="flex items-center gap-1.5 px-2 py-0.5 border border-border text-[9px] font-black uppercase tracking-widest text-muted">
              <Heart size={10} />
              Private
            </div>
          </div>
          
          <h1 className="text-5xl md:text-7xl font-black uppercase tracking-tighter truncate">Liked Tracks</h1>
          
          <p className="text-xs text-muted font-bold uppercase tracking-widest">
            Your personal archive of favorited transmissions.
          </p>

          <p className="text-[10px] font-bold uppercase tracking-widest text-muted">
             Tracks: {tracks.length}
          </p>
        </div>
      </header>

      <section>
        {tracks.length === 0 ? (
           <div className="border border-border border-dashed p-16 text-center">
             <p className="text-[10px] font-black uppercase tracking-widest text-muted">You haven't liked any tracks yet.</p>
           </div>
        ) : (
          <div className="flex flex-col border border-border">
            <div className="flex items-center gap-4 px-6 py-3 border-b border-border bg-surface/50 text-[9px] font-black uppercase tracking-widest text-muted">
              <div className="w-8 text-center">#</div>
              <div className="flex-1">Title / Author</div>
              <div className="w-16 text-right">Time</div>
            </div>
            
            {tracks.map((track, idx) => (
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
              </div>
            ))}
          </div>
        )}
      </section>
    </div>
  );
};

export default LikedTracksPage;