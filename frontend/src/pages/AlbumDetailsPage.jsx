import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Play, Clock, Music, Disc, ChevronLeft, Calendar } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import usePlayerStore from '../store/playerStore';

const AlbumDetailsPage = () => {
  const { albumId } = useParams();
  const navigate = useNavigate();
  const { playTrack, currentTrack, isPlaying } = usePlayerStore();
  const [album, setAlbum] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchAlbum = async () => {
      try {
        const response = await axiosClient.get(`/api/albums/${albumId}`);
        setAlbum(response.data);
      } catch (error) {
        console.error('Failed to fetch album:', error);
      } finally {
        setLoading(false);
      }
    };
    fetchAlbum();
  }, [albumId]);

  const formatDuration = (seconds) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs.toString().padStart(2, '0')}`;
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[70vh]">
        <Disc className="animate-spin text-primary h-12 w-12" />
      </div>
    );
  }

  if (!album) {
    return (
      <div className="text-center py-20">
        <h2 className="text-2xl font-bold">Album not found</h2>
        <button 
          onClick={() => navigate('/')}
          className="mt-4 text-primary hover:underline"
        >
          Go back home
        </button>
      </div>
    );
  }

  return (
    <div className="space-y-24 pb-32">
      <button 
        onClick={() => navigate(-1)}
        className="flex items-center gap-2 text-muted hover:text-white transition-colors group text-[10px] font-black uppercase tracking-widest"
      >
        <ChevronLeft size={14} className="group-hover:-translate-x-1 transition-transform" />
        <span>Return</span>
      </button>

      {/* Editorial Album Header */}
      <div className="flex flex-col md:flex-row gap-16 items-start">
        <div className="relative w-64 h-64 md:w-96 md:h-96 flex-shrink-0 bg-surface border border-border overflow-hidden">
          {album.coverUrl ? (
            <img 
              src={album.coverUrl} 
              alt={album.title} 
              className="w-full h-full object-cover grayscale brightness-90"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <Disc size={100} className="text-zinc-800" />
            </div>
          )}
        </div>

        <div className="flex-1 space-y-8 pt-4">
          <div className="inline-block px-3 py-1 border border-border text-[9px] font-black uppercase tracking-[0.3em] text-muted">
            Archive Mode
          </div>
          <h1 className="text-6xl md:text-8xl font-black tracking-tighter text-white uppercase leading-[0.85]">{album.title}</h1>
          
          <div className="space-y-4">
            <div className="flex items-center gap-4">
              <div className="w-8 h-8 bg-zinc-800 flex items-center justify-center text-[10px] text-white font-black">
                {album.authorName.charAt(0)}
              </div>
              <span className="text-xs font-black uppercase tracking-widest text-white">{album.authorName}</span>
            </div>
            
            <div className="flex flex-wrap gap-x-8 gap-y-2 text-[10px] font-bold uppercase tracking-[0.2em] text-muted">
              <div className="flex items-center gap-2">
                <Calendar size={12} />
                <span>{new Date(album.releaseDate).getFullYear()}</span>
              </div>
              <span>{album.tracks.length} Transmissions</span>
              <span>High Fidelity</span>
            </div>
          </div>
        </div>
      </div>

      {/* Tracks List - Minimalist Row Design */}
      <div className="border-t border-border">
        <div className="grid grid-cols-[4rem_1fr_6rem] gap-4 py-6 border-b border-border text-[9px] font-black uppercase tracking-[0.3em] text-muted px-4">
          <div>ID</div>
          <div>Transmission Title</div>
          <div className="text-right">Time</div>
        </div>

        <div className="divide-y divide-border/30">
          {album.tracks.map((track, index) => {
            const isCurrent = currentTrack?.id === track.id;
            const active = isCurrent && isPlaying;

            return (
              <div 
                key={track.id}
                className={`grid grid-cols-[4rem_1fr_6rem] gap-4 py-6 px-4 items-center hover:bg-white/[0.02] transition-colors group cursor-pointer ${
                  isCurrent ? 'bg-white/5' : ''
                }`}
                onClick={() => playTrack(track, album.tracks)}
              >
                <div className="text-[10px] font-bold text-muted tabular-nums">
                  {(index + 1).toString().padStart(2, '0')}
                </div>

                <div className="flex flex-col min-w-0">
                  <span className={`text-xs font-black uppercase tracking-tight truncate ${isCurrent ? 'text-white' : 'text-zinc-400 group-hover:text-white transition-colors'}`}>
                    {track.title}
                  </span>
                </div>

                <div className="text-right">
                   <span className="text-[10px] font-bold text-muted tabular-nums">
                     {formatDuration(track.duration)}
                   </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
};

export default AlbumDetailsPage;
