import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Play, Clock, Music, Disc, ChevronLeft, Calendar, Trash2, Loader2 } from 'lucide-react';
import axiosClient from '../api/axiosClient';
import usePlayerStore from '../store/playerStore';
import useAuthStore from '../store/authStore';
import ConfirmDialog from '../components/modals/ConfirmDialog';
import toast from 'react-hot-toast';

const AlbumDetailsPage = () => {
  const { albumId } = useParams();
  const navigate = useNavigate();
  const { user: currentUser } = useAuthStore();
  const { playTrack, currentTrack, isPlaying } = usePlayerStore();
  const [album, setAlbum] = useState(null);
  const [loading, setLoading] = useState(true);
  const [isDeleting, setIsDeleting] = useState(false);
  const [showConfirm, setShowConfirm] = useState(false);

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

  const handleDelete = async () => {
    setIsDeleting(true);
    setShowConfirm(false);
    try {
      await axiosClient.delete(`/api/albums/${albumId}`);
      toast.success('COLLECTION_PURGED: All transmissions erased.');
      navigate('/playlists');
    } catch (error) {
      console.error('Purge failed:', error);
      toast.error('PURGE_FAILED: Protocol error during deletion.');
    } finally {
      setIsDeleting(false);
    }
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-[70vh]">
        <Disc className="animate-spin text-zinc-900 dark:text-white h-12 w-12" />
      </div>
    );
  }

  if (!album) {
    return (
      <div className="text-center py-20 text-zinc-900 dark:text-white">
        <h2 className="text-2xl font-bold">Album not found</h2>
        <button 
          onClick={() => navigate('/')}
          className="mt-4 text-zinc-900 dark:text-white hover:underline"
        >
          Go back home
        </button>
      </div>
    );
  }

  const isOwner = currentUser?.userId === album.authorId;

  return (
    <div className="space-y-24 pb-32">
      <div className="flex items-center justify-between">
        <button 
          onClick={() => navigate(-1)}
          className="flex items-center gap-2 text-zinc-500 dark:text-zinc-400 hover:text-zinc-900 dark:hover:text-white transition-colors group text-[10px] font-black uppercase tracking-widest"
        >
          <ChevronLeft size={14} className="group-hover:-translate-x-1 transition-transform" />
          <span>Return</span>
        </button>

        {isOwner && (
          <button 
            onClick={() => setShowConfirm(true)}
            disabled={isDeleting}
            className="flex items-center gap-2 text-red-500 hover:text-red-600 dark:hover:text-red-400 transition-colors text-[10px] font-black uppercase tracking-[0.2em] border border-red-500/30 px-4 py-2 hover:bg-red-500/10"
          >
            {isDeleting ? <Loader2 size={14} className="animate-spin" /> : <Trash2 size={14} />}
            <span>Purge Collection</span>
          </button>
        )}
      </div>

      {/* Editorial Album Header */}
      <div className="flex flex-col md:flex-row gap-16 items-start">
        <div className="relative w-64 h-64 md:w-96 md:h-96 flex-shrink-0 bg-zinc-50 dark:bg-zinc-900 border border-zinc-200 dark:border-zinc-800 overflow-hidden">
          {album.coverUrl ? (
            <img 
              src={album.coverUrl} 
              alt={album.title} 
              className="w-full h-full object-cover grayscale brightness-90"
            />
          ) : (
            <div className="w-full h-full flex items-center justify-center">
              <Disc size={100} className="text-zinc-300 dark:text-zinc-800" />
            </div>
          )}
        </div>

        <div className="flex-1 space-y-8 pt-4">
          <div className="inline-block px-3 py-1 border border-zinc-200 dark:border-zinc-800 text-[9px] font-black uppercase tracking-[0.3em] text-zinc-500 dark:text-zinc-400">
            Archive Mode
          </div>
          <h1 className="text-6xl md:text-8xl font-black tracking-tighter text-zinc-900 dark:text-white uppercase leading-[0.85]">{album.title}</h1>
          
          <div className="space-y-4">
            <div className="flex items-center gap-4">
              <div className="w-8 h-8 bg-zinc-200 dark:bg-zinc-800 flex items-center justify-center text-[10px] text-zinc-900 dark:text-white font-black">
                {album.authorName.charAt(0)}
              </div>
              <span className="text-xs font-black uppercase tracking-widest text-zinc-900 dark:text-white">{album.authorName}</span>
            </div>
            
            <div className="flex flex-wrap gap-x-8 gap-y-2 text-[10px] font-bold uppercase tracking-[0.2em] text-zinc-500 dark:text-zinc-400">
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
      <div className="border-t border-zinc-200 dark:border-zinc-800">
        <div className="grid grid-cols-[4rem_1fr_6rem] gap-4 py-6 border-b border-zinc-200 dark:border-zinc-800 text-[9px] font-black uppercase tracking-[0.3em] text-zinc-500 dark:text-zinc-400 px-4">
          <div>ID</div>
          <div>Transmission Title</div>
          <div className="text-right">Time</div>
        </div>

        <div className="divide-y divide-zinc-200 dark:divide-zinc-800/30">
          {album.tracks.map((track, index) => {
            const isCurrent = currentTrack?.id === track.id;
            const active = isCurrent && isPlaying;

            return (
              <div 
                key={track.id}
                className={`grid grid-cols-[4rem_1fr_6rem] gap-4 py-6 px-4 items-center hover:bg-zinc-100 dark:hover:bg-white/[0.02] transition-colors group cursor-pointer ${
                  isCurrent ? 'bg-zinc-100 dark:bg-white/5' : ''
                }`}
                onClick={() => playTrack(track, album.tracks)}
              >
                <div className="text-[10px] font-bold text-zinc-500 dark:text-zinc-400 tabular-nums">
                  {(index + 1).toString().padStart(2, '0')}
                </div>

                <div className="flex flex-col min-w-0">
                  <span className={`text-xs font-black uppercase tracking-tight truncate ${isCurrent ? 'text-zinc-900 dark:text-white' : 'text-zinc-600 dark:text-zinc-400 group-hover:text-zinc-900 dark:group-hover:text-white transition-colors'}`}>
                    {track.title}
                  </span>
                </div>

                <div className="text-right">
                   <span className="text-[10px] font-bold text-zinc-500 dark:text-zinc-400 tabular-nums">
                     {formatDuration(track.duration)}
                   </span>
                </div>
              </div>
            );
          })}
        </div>
      </div>

      {isDeleting && (
        <div className="fixed inset-0 z-[150] bg-white/80 dark:bg-black/80 backdrop-blur-md flex flex-col items-center justify-center space-y-8">
          <Loader2 size={48} className="animate-spin text-zinc-900 dark:text-white" />
          <p className="text-[10px] font-black uppercase tracking-[0.5em] text-zinc-900 dark:text-white">Executing Purge Sequence</p>
        </div>
      )}

      <ConfirmDialog 
        isOpen={showConfirm}
        title="WARNING: IDENTITY_PURGE"
        message="WARNING: Purging this collection will permanently erase all associated audio transmissions from the network. Proceed?"
        confirmText="Execute"
        cancelText="Abort"
        onConfirm={handleDelete}
        onCancel={() => setShowConfirm(false)}
      />
    </div>
  );
};

export default AlbumDetailsPage;
